/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.oauth

import co.artise.android.workspace.api.WorkspaceConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.auth.OAuthStaticClient
import io.element.android.libraries.matrix.api.auth.OAuthStaticClientProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * Signs in with the workspace's own client when its workspace file has `oauth` (see [co.artise.android.workspace.api.WorkspaceOAuth]),
 * so its sign-in service skips the consent page; same as iOS. The client is registered for the sign-in service's issuer, which comes
 * from the homeserver's auth metadata.
 *
 * Only in the release app: the https redirect is an App Link verified for [RELEASE_APPLICATION_ID] alone, so other builds
 * (debug, nightly) wouldn't get the result back and keep registering themselves with their custom scheme.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WorkspaceOAuthStaticClientProvider(
    private val buildMeta: BuildMeta,
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) : OAuthStaticClientProvider {
    @Volatile
    override var pendingRedirectUri: String? = null
        private set

    override suspend fun get(serverName: String, homeserverUrl: String): OAuthStaticClient? {
        pendingRedirectUri = null
        if (buildMeta.applicationId != RELEASE_APPLICATION_ID) return null
        val oauth = workspaceConfigRepository.fetch(serverName)?.oauth ?: return null
        val issuer = issuerOf(homeserverUrl) ?: return null
        return OAuthStaticClient(issuer = issuer, clientId = oauth.androidClientId, redirectUri = oauth.redirectUri)
            .also { pendingRedirectUri = it.redirectUri }
    }

    /** The sign-in service's `issuer`, from the homeserver's auth metadata; null when it can't be read (then the app registers itself). */
    private suspend fun issuerOf(homeserverUrl: String): String? = withContext(dispatchers.io) {
        val url = homeserverUrl.trimEnd('/') + AUTH_METADATA_PATH
        try {
            val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Auth metadata of $homeserverUrl: HTTP ${response.code}")
                    return@use null
                }
                val metadata = runCatchingExceptions { Json.parseToJsonElement(response.body.string()) }.getOrNull() as? JsonObject
                (metadata?.get("issuer") as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.startsWith("https://") }
            }
        } catch (e: IOException) {
            Timber.w(e, "Auth metadata of $homeserverUrl unavailable")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Auth metadata of $homeserverUrl: bad address")
            null
        }
    }

    companion object {
        /** The release app, the only one join.artise.co's assetlinks.json names. */
        const val RELEASE_APPLICATION_ID = "co.artise.app"
        private const val AUTH_METADATA_PATH = "/_matrix/client/v1/auth_metadata"
    }
}
