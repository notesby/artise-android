/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/** The ways into Artise ID the app offers a button for, with the key the server's `providers` uses. */
enum class ArtiseIdProvider(val key: String) {
    GOOGLE("google"),
    MICROSOFT("microsoft"),
    APPLE("apple"),
}

/** How to sign in to Artise ID, from `GET https://api.artise.co/v1/sign-in`. */
data class ArtiseIdConfig(
    val issuer: String,
    val clientId: String,
    /** https://join.artise.co/oauth, the same App Link a workspace's own client returns to. */
    val redirectUri: String,
    val scopes: List<String>,
    /** The Artise ID identity provider id of each provider offered; one missing has no button. */
    val providers: Map<ArtiseIdProvider, String>,
)

/**
 * Reads [ArtiseIdConfig], kept for an hour. Null when it's missing (404, offline, not understood): sign-in then starts with typing the
 * workspace's name, as before Artise ID. Failures aren't kept, so the next sign-in screen asks again.
 */
class ArtiseIdConfigClient(
    private val okHttpClient: OkHttpClient,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val url: String = SIGN_IN_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: Pair<ArtiseIdConfig, Long>? = null

    suspend fun get(): ArtiseIdConfig? {
        cached?.takeIf { (_, untilMillis) -> clock.epochMillis() < untilMillis }?.let { return it.first }
        return withContext(dispatchers.io) { download() }?.also { cached = it to clock.epochMillis() + CACHE_MILLIS }
    }

    private fun download(): ArtiseIdConfig? = try {
        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                parse(response.body.string())
            } else {
                Timber.i("Artise ID sign-in settings: HTTP ${response.code}")
                null
            }
        }
    } catch (e: IOException) {
        Timber.w(e, "Artise ID sign-in settings unavailable")
        null
    }

    private fun parse(body: String): ArtiseIdConfig? {
        val dto = try {
            json.decodeFromString<SignInDto>(body)
        } catch (e: SerializationException) {
            Timber.w(e, "Artise ID sign-in settings not understood")
            return null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Artise ID sign-in settings not understood")
            return null
        }
        if (!dto.issuer.startsWith("https://") || !dto.redirectUri.startsWith("https://") || dto.clientId.isBlank()) return null
        return ArtiseIdConfig(
            issuer = dto.issuer,
            clientId = dto.clientId,
            redirectUri = dto.redirectUri,
            scopes = dto.scopes,
            providers = ArtiseIdProvider.entries
                .mapNotNull { provider -> dto.providers[provider.key]?.takeIf { it.isNotBlank() }?.let { provider to it } }
                .toMap(),
        )
    }

    @Serializable
    private data class SignInDto(
        val issuer: String,
        @SerialName("client_id") val clientId: String,
        @SerialName("redirect_uri") val redirectUri: String,
        val scopes: List<String> = listOf("openid"),
        val providers: Map<String, String> = emptyMap(),
    )

    companion object {
        const val SIGN_IN_URL = "https://api.artise.co/v1/sign-in"
        private const val CACHE_MILLIS = 60 * 60 * 1000L
    }
}
