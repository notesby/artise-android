/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps each account's workspace settings, read from `https://{server}/.well-known/matrix/client` (the server is the one in the
 * account's Matrix ID). Saved on the phone, so the app opens with the right look offline; fetched again when older than the server's
 * `max-age`: the first time an account's settings are read, and each time the app comes to the foreground. Admins can change them at
 * any time; the change shows the next time the app opens.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultWorkspaceConfigRepository(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    @AppCoroutineScope private val appScope: CoroutineScope,
    appForegroundStateService: AppForegroundStateService,
) : WorkspaceConfigRepository {
    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    // What's saved, read once per account; a key with no value means "this server has no settings".
    private val loaded = ConcurrentHashMap<SessionId, MutableStateFlow<WorkspaceConfig?>>()

    // The default argument is to let tests point at a local server.
    internal var wellKnownUrlFor: (serverName: String) -> String = { "https://$it/.well-known/matrix/client" }

    init {
        // Back in the foreground: check every account in use (each only if its copy is older than max-age).
        appForegroundStateService.isInForeground
            .drop(1)
            .filter { it }
            .onEach { loaded.keys.forEach { sessionId -> refresh(sessionId) } }
            .launchIn(appScope)
    }

    override fun cached(sessionId: SessionId): WorkspaceConfig? = flowFor(sessionId).value

    override fun configFlow(sessionId: SessionId): Flow<WorkspaceConfig?> = flowFor(sessionId).map { it }

    override suspend fun refresh(sessionId: SessionId, force: Boolean) {
        val fresh = preferences.getLong(sessionId.untilKey(), 0) > clock.epochMillis()
        if (fresh && !force) return
        val answer = download(serverNameOf(sessionId)) ?: return
        preferences.edit {
            putString(sessionId.fileKey(), answer.file)
            putLong(sessionId.untilKey(), clock.epochMillis() + answer.maxAgeSeconds * MILLIS)
        }
        flowFor(sessionId).value = WorkspaceFileParser.parse(answer.file)
    }

    override suspend fun fetch(serverName: String): WorkspaceConfig? = download(serverName)?.file?.let(WorkspaceFileParser::parse)

    override fun clear(sessionId: SessionId) {
        preferences.edit {
            remove(sessionId.fileKey())
            remove(sessionId.untilKey())
        }
        loaded.remove(sessionId)
    }

    private fun flowFor(sessionId: SessionId): MutableStateFlow<WorkspaceConfig?> = loaded[sessionId] ?: run {
        val flow = MutableStateFlow(preferences.getString(sessionId.fileKey(), null)?.let(WorkspaceFileParser::parse))
        loaded.putIfAbsent(sessionId, flow) ?: flow.also { appScope.launch { refresh(sessionId) } }
    }

    private data class Answer(val file: String, val maxAgeSeconds: Long)

    /** The server's whole `.well-known/matrix/client`, or null when it can't be read (then what's saved stays). */
    private suspend fun download(serverName: String): Answer? = withContext(dispatchers.io) {
        val request = Request.Builder().url(wellKnownUrlFor(serverName)).cacheControl(CacheControl.FORCE_NETWORK).get().build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Workspace file of $serverName: HTTP ${response.code}")
                    return@use null
                }
                val maxAge = response.cacheControl.maxAgeSeconds.takeIf { it > 0 } ?: DEFAULT_MAX_AGE_SECONDS
                Answer(response.body.string(), maxAge.toLong())
            }
        } catch (e: IOException) {
            Timber.w(e, "Workspace file of $serverName unavailable")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Workspace file of $serverName: bad address")
            null
        }
    }

    private fun SessionId.fileKey() = "$value.file"

    private fun SessionId.untilKey() = "$value.until"

    private companion object {
        const val PREFERENCES = "artise_workspace"
        const val DEFAULT_MAX_AGE_SECONDS = 300
        const val MILLIS = 1000L

        /** The server part of "@ana:acme.artise.co". */
        fun serverNameOf(sessionId: SessionId): String = sessionId.value.substringAfter(':')
    }
}
