/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import co.artise.android.workspace.api.ArtiseIdCallback
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/** Where an Artise ID sign-in is. */
sealed interface ArtiseIdSignInStatus {
    data object Idle : ArtiseIdSignInStatus

    /** Artise ID's page is open in the browser. */
    data object InBrowser : ArtiseIdSignInStatus

    /** Back from the browser, trading the code for tokens. */
    data object Finishing : ArtiseIdSignInStatus

    data object SignedIn : ArtiseIdSignInStatus

    data object Failed : ArtiseIdSignInStatus
}

/** One of the person's workspaces, from `/v1/me/workspaces`. */
data class ArtiseIdWorkspace(val slug: String, val name: String, val serverName: String, val homeserverUrl: String)

/** From `GET /v1/me/security`: [twoStep] counts an authenticator app, an email code or a security key; [provider] is google, microsoft, apple or null. */
data class ArtiseIdSecurity(val twoStep: Boolean, val methods: List<String>, val provider: String?)

sealed interface ArtiseIdWorkspaces {
    data class Found(val workspaces: List<ArtiseIdWorkspace>) : ArtiseIdWorkspaces

    /** No tokens, or Artise ID no longer takes them: sign in again. */
    data object SignedOut : ArtiseIdWorkspaces

    data object Unreachable : ArtiseIdWorkspaces
}

/**
 * Artise ID sign-in: OIDC authorization code with PKCE (S256) and no client secret, in the same browser (Custom Tab) as a workspace's
 * sign-in, so Artise ID's session carries over and opening a workspace afterwards shows no page. The return comes on the same App Link as
 * a workspace's own client (https://join.artise.co/oauth) and is recognised by its `state`.
 */
class ArtiseIdAuth(
    private val configClient: ArtiseIdConfigClient,
    private val tokenStore: ArtiseIdTokenStore,
    private val okHttpClient: OkHttpClient,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val appScope: CoroutineScope,
    private val workspacesUrl: String = WORKSPACES_URL,
    private val securityUrl: String = SECURITY_URL,
) : ArtiseIdCallback {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    private data class Endpoints(val authorization: String, val token: String, val revocation: String?)

    private data class Pending(val state: String, val verifier: String, val redirectUri: String, val clientId: String, val tokenEndpoint: String)

    @Volatile
    private var endpoints: Pair<String, Endpoints>? = null

    @Volatile
    private var pending: Pending? = null

    private val mutableStatus = MutableStateFlow<ArtiseIdSignInStatus>(ArtiseIdSignInStatus.Idle)
    val status: StateFlow<ArtiseIdSignInStatus> = mutableStatus.asStateFlow()

    /** The address to open in the browser to sign in, straight to [provider]'s page (or Artise ID's own, for email); null on failure. */
    suspend fun authorizationUrl(config: ArtiseIdConfig, provider: ArtiseIdProvider?): String? {
        val found = endpointsOf(config.issuer) ?: return null
        val verifier = randomToken()
        val state = randomToken()
        val scopes = config.scopes + listOfNotNull(provider?.let { config.providers[it] }?.let { PROVIDER_SCOPE_PREFIX + it })
        val url = found.authorization.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("response_type", "code")
            ?.addQueryParameter("client_id", config.clientId)
            ?.addQueryParameter("redirect_uri", config.redirectUri)
            ?.addQueryParameter("scope", scopes.distinct().joinToString(" "))
            ?.addQueryParameter("state", state)
            ?.addQueryParameter("code_challenge", challengeOf(verifier))
            ?.addQueryParameter("code_challenge_method", "S256")
            // After signing out of Artise ID, ask again rather than reuse the browser's session.
            ?.apply { if (tokenStore.isFreshSignInNeeded()) addQueryParameter("prompt", "login") }
            ?.build()
            ?: return null
        pending = Pending(state, verifier, config.redirectUri, config.clientId, found.token)
        mutableStatus.value = ArtiseIdSignInStatus.InBrowser
        return url.toString()
    }

    /** The browser was closed without an answer. */
    fun cancel() {
        if (mutableStatus.value != ArtiseIdSignInStatus.InBrowser) return
        pending = null
        mutableStatus.value = ArtiseIdSignInStatus.Idle
    }

    /** The screen has shown [ArtiseIdSignInStatus.SignedIn] or [ArtiseIdSignInStatus.Failed]. */
    fun acknowledge() {
        if (mutableStatus.value == ArtiseIdSignInStatus.SignedIn || mutableStatus.value == ArtiseIdSignInStatus.Failed) {
            mutableStatus.value = ArtiseIdSignInStatus.Idle
        }
    }

    override fun handles(url: String): Boolean {
        val pending = pending ?: return false
        if (!url.startsWith(pending.redirectUri + "?")) return false
        return url.toHttpUrlOrNull()?.queryParameter("state") == pending.state
    }

    override fun onCallback(url: String) {
        if (!handles(url)) return
        val pending = pending ?: return
        this.pending = null
        val answer = url.toHttpUrlOrNull()
        val code = answer?.queryParameter("code")
        if (code == null) {
            // Cancelled on Artise ID's page (error=access_denied) or another error: back to the buttons.
            Timber.i("Artise ID sign-in ended without a code: ${answer?.queryParameter("error")}")
            mutableStatus.value = if (answer?.queryParameter("error") == "access_denied") ArtiseIdSignInStatus.Idle else ArtiseIdSignInStatus.Failed
            return
        }
        mutableStatus.value = ArtiseIdSignInStatus.Finishing
        appScope.launch {
            val tokens = requestTokens(
                pending.tokenEndpoint,
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", pending.redirectUri)
                    .add("client_id", pending.clientId)
                    .add("code_verifier", pending.verifier)
                    .build(),
            )
            if (tokens != null) {
                tokenStore.save(tokens)
                tokenStore.setFreshSignInNeeded(false)
            }
            mutableStatus.value = if (tokens != null) ArtiseIdSignInStatus.SignedIn else ArtiseIdSignInStatus.Failed
        }
    }

    suspend fun hasTokens(): Boolean = tokenStore.load() != null

    /** The person's workspaces, with a refresh of the access token when it has expired (or is refused) and a refresh token was given. */
    suspend fun workspaces(): ArtiseIdWorkspaces {
        var tokens = tokenStore.load() ?: return ArtiseIdWorkspaces.SignedOut
        val expired = tokens.expiresAtMillis?.let { clock.epochMillis() >= it } ?: false
        if (expired) tokens = refreshed(tokens) ?: return signedOut()
        return when (val answer = requestWorkspaces(tokens.accessToken)) {
            WorkspacesAnswer.Unauthorized -> {
                val again = refreshed(tokens) ?: return signedOut()
                when (val retry = requestWorkspaces(again.accessToken)) {
                    WorkspacesAnswer.Unauthorized -> signedOut()
                    is WorkspacesAnswer.Done -> retry.result
                }
            }
            is WorkspacesAnswer.Done -> answer.result
        }
    }

    /** Forgets Artise ID's tokens (at the last workspace's sign-out). */
    suspend fun signOut() {
        pending = null
        tokenStore.load()?.refreshToken?.let { revoke(it) }
        tokenStore.clear()
        tokenStore.setFreshSignInNeeded(true)
    }

    /** Cancels [refreshToken] on Artise ID, so a copy of it is useless; best effort (offline, it simply expires). */
    private suspend fun revoke(refreshToken: String) {
        val config = configClient.get() ?: return
        val revocation = endpointsOf(config.issuer)?.revocation ?: return
        withContext(dispatchers.io) {
            runCatchingExceptions {
                val form = FormBody.Builder()
                    .add("token", refreshToken)
                    .add("token_type_hint", "refresh_token")
                    .add("client_id", config.clientId)
                    .build()
                okHttpClient.newCall(Request.Builder().url(revocation).post(form).build()).execute().close()
            }.onFailure { Timber.w(it, "Couldn't revoke the Artise ID refresh token") }
        }
    }

    /** The person's two-step verification and sign-in provider (Seguridad), refreshing the token like [workspaces]; null when unknown. */
    suspend fun security(): ArtiseIdSecurity? {
        var tokens = tokenStore.load() ?: return null
        val expired = tokens.expiresAtMillis?.let { clock.epochMillis() >= it } ?: false
        if (expired) tokens = refreshed(tokens) ?: return null
        return when (val answer = requestSecurity(tokens.accessToken)) {
            SecurityAnswer.Unauthorized -> refreshed(tokens)?.let { (requestSecurity(it.accessToken) as? SecurityAnswer.Done)?.security }
            is SecurityAnswer.Done -> answer.security
        }
    }

    private sealed interface SecurityAnswer {
        data object Unauthorized : SecurityAnswer

        data class Done(val security: ArtiseIdSecurity?) : SecurityAnswer
    }

    private suspend fun requestSecurity(accessToken: String): SecurityAnswer = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(securityUrl)
            .header("Authorization", "Bearer " + accessToken)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_UNAUTHORIZED -> SecurityAnswer.Unauthorized
                    !response.isSuccessful -> SecurityAnswer.Done(null)
                    else -> SecurityAnswer.Done(decode<SecurityDto>(response.body.string())?.let {
                        ArtiseIdSecurity(twoStep = it.twoStep, methods = it.methods, provider = it.provider)
                    })
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Artise ID security unavailable")
            SecurityAnswer.Done(null)
        }
    }

    private suspend fun signedOut(): ArtiseIdWorkspaces {
        tokenStore.clear()
        return ArtiseIdWorkspaces.SignedOut
    }

    // Artise ID rotates refresh tokens: two refreshes at once would make the second one fail, so they wait for each other.
    private val refreshLock = Mutex()

    private suspend fun refreshed(tokens: ArtiseIdTokens): ArtiseIdTokens? = refreshLock.withLock {
        // Another caller may have refreshed while this one waited: use its tokens.
        tokenStore.load()?.takeIf { it.accessToken != tokens.accessToken }?.let { return@withLock it }
        refreshedLocked(tokens)
    }

    private suspend fun refreshedLocked(tokens: ArtiseIdTokens): ArtiseIdTokens? {
        val refreshToken = tokens.refreshToken ?: return null
        val config = configClient.get() ?: return null
        val tokenEndpoint = endpointsOf(config.issuer)?.token ?: return null
        val fresh = requestTokens(
            tokenEndpoint,
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", config.clientId)
                .build(),
        ) ?: return null
        // A refresh answer may leave the refresh token out: the old one stays valid then.
        return fresh.copy(refreshToken = fresh.refreshToken ?: refreshToken).also { tokenStore.save(it) }
    }

    private sealed interface WorkspacesAnswer {
        data object Unauthorized : WorkspacesAnswer

        data class Done(val result: ArtiseIdWorkspaces) : WorkspacesAnswer
    }

    private suspend fun requestWorkspaces(accessToken: String): WorkspacesAnswer = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(workspacesUrl)
            .header("Authorization", "Bearer $accessToken")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_UNAUTHORIZED -> WorkspacesAnswer.Unauthorized
                    response.isSuccessful -> WorkspacesAnswer.Done(parseWorkspaces(response) ?: ArtiseIdWorkspaces.Unreachable)
                    else -> {
                        Timber.w("Artise ID workspaces: HTTP ${response.code}")
                        WorkspacesAnswer.Done(ArtiseIdWorkspaces.Unreachable)
                    }
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Artise ID workspaces unavailable")
            WorkspacesAnswer.Done(ArtiseIdWorkspaces.Unreachable)
        }
    }

    private fun parseWorkspaces(response: Response): ArtiseIdWorkspaces? = decode<WorkspacesDto>(response.body.string())?.let { dto ->
        ArtiseIdWorkspaces.Found(dto.workspaces.map { ArtiseIdWorkspace(it.slug, it.name, it.serverName, it.homeserverUrl) })
    }

    private suspend fun requestTokens(tokenEndpoint: String, form: FormBody): ArtiseIdTokens? = withContext(dispatchers.io) {
        val request = Request.Builder().url(tokenEndpoint).post(form).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Artise ID token request: HTTP ${response.code}")
                    return@use null
                }
                decode<TokenDto>(response.body.string())?.let { dto ->
                    ArtiseIdTokens(
                        accessToken = dto.accessToken,
                        refreshToken = dto.refreshToken,
                        expiresAtMillis = dto.expiresIn?.let { clock.epochMillis() + it * MILLIS },
                    )
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Artise ID token request failed")
            null
        }
    }

    private suspend fun endpointsOf(issuer: String): Endpoints? {
        endpoints?.takeIf { it.first == issuer }?.let { return it.second }
        return withContext(dispatchers.io) {
            val request = Request.Builder().url(issuer.trimEnd('/') + DISCOVERY_PATH).get().build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Artise ID discovery: HTTP ${response.code}")
                        return@use null
                    }
                    val dto = decode<DiscoveryDto>(response.body.string()) ?: return@use null
                    // The discovery document must be the issuer's own, with https endpoints.
                    if (dto.issuer.trimEnd('/') != issuer.trimEnd('/')) return@use null
                    if (!dto.authorizationEndpoint.startsWith("https://") || !dto.tokenEndpoint.startsWith("https://")) return@use null
                    Endpoints(dto.authorizationEndpoint, dto.tokenEndpoint, dto.revocationEndpoint?.takeIf { it.startsWith("https://") })
                }
            } catch (e: IOException) {
                Timber.w(e, "Artise ID discovery failed")
                null
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Artise ID discovery: bad address")
                null
            }
        }?.also { endpoints = issuer to it }
    }

    private inline fun <reified T> decode(body: String): T? = try {
        json.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        Timber.w(e, "Artise ID answer not understood")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Artise ID answer not understood")
        null
    }

    private fun randomToken(): String = ByteArray(RANDOM_BYTES).also(random::nextBytes).let(BASE64_URL::encode)

    @Serializable
    private data class DiscoveryDto(
        val issuer: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    )

    @Serializable
    private data class SecurityDto(
        @SerialName("two_step") val twoStep: Boolean = false,
        val methods: List<String> = emptyList(),
        val provider: String? = null,
    )

    @Serializable
    private data class TokenDto(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
    )

    @Serializable
    private data class WorkspacesDto(val workspaces: List<WorkspaceDto>)

    @Serializable
    private data class WorkspaceDto(
        val slug: String,
        val name: String,
        @SerialName("server_name") val serverName: String,
        @SerialName("homeserver_url") val homeserverUrl: String,
    )

    companion object {
        const val WORKSPACES_URL = "https://api.artise.co/v1/me/workspaces"
        const val SECURITY_URL = "https://api.artise.co/v1/me/security"

        /** Added to the scopes to open one identity provider (Apple, Google, Microsoft) directly. */
        const val PROVIDER_SCOPE_PREFIX = "urn:zitadel:iam:org:idp:id:"
        private const val DISCOVERY_PATH = "/.well-known/openid-configuration"
        private const val RANDOM_BYTES = 32
        private const val HTTP_UNAUTHORIZED = 401
        private const val MILLIS = 1000L
        private val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        /** PKCE S256: the unpadded base64url SHA-256 of the verifier. */
        fun challengeOf(verifier: String): String = BASE64_URL.encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}
