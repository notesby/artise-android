/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.Workspace
import co.artise.android.workspace.api.WorkspaceLookup
import co.artise.android.workspace.api.WorkspaceLookupResult
import co.artise.android.workspace.api.WorkspaceSlug
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
import java.util.concurrent.ConcurrentHashMap

/**
 * `GET https://api.artise.co/v1/workspaces/{slug}`: public, no sign-in. Answers are kept as long as the server's `max-age` says
 * (5 minutes), so going back and forth on the sign-in screen doesn't ask again.
 */
class WorkspaceLookupClient(
    private val okHttpClient: OkHttpClient,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val baseUrl: String = LOOKUP_BASE_URL,
) : WorkspaceLookup {
    private val json = Json { ignoreUnknownKeys = true }

    private data class Cached(val result: WorkspaceLookupResult, val untilMillis: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    override suspend fun find(input: String): WorkspaceLookupResult {
        val typed = WorkspaceSlug.normalize(input)
        // A dot means a server name ("artise.co"), which the lookup can't take: go to that server directly.
        if ('.' in typed) return serverNameOf(typed)?.let { WorkspaceLookupResult.ServerName(it) } ?: WorkspaceLookupResult.Invalid
        if (!WorkspaceSlug.isValid(typed)) return WorkspaceLookupResult.Invalid
        cache[typed]?.takeIf { clock.epochMillis() < it.untilMillis }?.let { return it.result }
        return withContext(dispatchers.io) { request(typed) }
    }

    private fun request(slug: String): WorkspaceLookupResult {
        // The app keeps its own copy: OkHttp's cache isn't set up for every client.
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/" + slug).cacheControl(CacheControl.FORCE_NETWORK).get().build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val result = when (response.code) {
                    HTTP_OK -> parse(response.body.string())
                    HTTP_NOT_FOUND -> WorkspaceLookupResult.NotFound(slug)
                    HTTP_TOO_MANY_REQUESTS -> WorkspaceLookupResult.TooManyTries
                    else -> WorkspaceLookupResult.Unreachable
                }
                val maxAge = response.cacheControl.maxAgeSeconds
                if (maxAge > 0 && result !is WorkspaceLookupResult.Unreachable && result !is WorkspaceLookupResult.TooManyTries) {
                    cache[slug] = Cached(result, clock.epochMillis() + maxAge.coerceAtMost(MAX_CACHE_SECONDS) * MILLIS)
                }
                result
            }
        } catch (e: IOException) {
            Timber.w(e, "Workspace lookup failed")
            WorkspaceLookupResult.Unreachable
        }
    }

    private fun parse(body: String): WorkspaceLookupResult {
        val dto = try {
            json.decodeFromString<WorkspaceDto>(body)
        } catch (e: SerializationException) {
            Timber.w(e, "Workspace lookup answer not understood")
            return WorkspaceLookupResult.Unreachable
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Workspace lookup answer not understood")
            return WorkspaceLookupResult.Unreachable
        }
        val workspace = Workspace(
            slug = dto.slug,
            name = dto.name,
            serverName = dto.serverName,
            homeserverUrl = dto.homeserverUrl,
            isSuspended = dto.status == STATUS_SUSPENDED,
            joinUrl = dto.joinUrl?.takeIf { it.startsWith("https://") },
        )
        return if (workspace.isSuspended) WorkspaceLookupResult.Suspended(workspace) else WorkspaceLookupResult.Found(workspace)
    }

    @Serializable
    private data class WorkspaceDto(
        val slug: String,
        val name: String,
        @SerialName("server_name") val serverName: String,
        @SerialName("homeserver_url") val homeserverUrl: String,
        val status: String = "active",
        @SerialName("join_url") val joinUrl: String? = null,
    )

    companion object {
        const val LOOKUP_BASE_URL = "https://api.artise.co/v1/workspaces"
        private const val STATUS_SUSPENDED = "suspended"
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_CACHE_SECONDS = 300
        private const val MILLIS = 1000L
        private val SERVER_NAME = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+(:[0-9]{1,5})?$")

        /** "https://Artise.co/" → "artise.co"; null when it isn't a host name. */
        fun serverNameOf(typed: String): String? = typed.removePrefix("https://").trimEnd('/').takeIf { SERVER_NAME.matches(it) }
    }
}
