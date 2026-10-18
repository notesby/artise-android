/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import co.artise.android.notes.impl.attachments.PhotoConverter
import co.artise.android.notes.impl.attachments.PrefetchPolicy
import co.artise.android.notes.impl.auth.DefaultNotesTokenSource
import co.artise.android.notes.impl.db.NotesDatabase
import co.artise.android.notes.impl.di.NotesBindingContainer
import co.artise.android.notes.impl.local.NotesLocalStore
import co.artise.android.notes.impl.remote.NotesApiClient
import co.artise.android.notes.impl.sync.NotesSyncEngine
import co.artise.android.workspace.api.WorkspaceConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.network.interceptors.DynamicHttpLoggingInterceptor
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.encrypteddb.SqlCipherDriverFactory
import io.element.encrypteddb.passphrase.RandomDatabaseSecretProvider
import okhttp3.OkHttpClient

/**
 * One [NotesSyncEngine] per account for the whole app, so the screens, the background check while the app is open and the job while
 * it's closed share the same database, per-chat locks and change notifications instead of each opening their own.
 */
@SingleIn(AppScope::class)
@Inject
class NotesEngines(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val systemClock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val photoConverter: PhotoConverter,
    private val prefetchPolicy: PrefetchPolicy,
    private val analyticsService: AnalyticsService,
    private val workspaceConfigRepository: WorkspaceConfigRepository,
) {
    private data class Entry(val client: MatrixClient, val engine: NotesSyncEngine)

    private val entries = mutableMapOf<SessionId, Entry>()

    /** The account's engine, made the first time it's needed; a new client for the same account (signed in again) gets a new one. */
    fun forClient(client: MatrixClient): NotesSyncEngine = synchronized(entries) {
        entries[client.sessionId]?.takeIf { it.client === client }?.engine
            ?: create(client).also { entries[client.sessionId] = Entry(client, it) }
    }

    /** Forgets the account's engine at sign-out, before its database is deleted. */
    fun remove(sessionId: SessionId) {
        synchronized(entries) { entries.remove(sessionId) }
    }

    private fun create(client: MatrixClient): NotesSyncEngine {
        val userId = client.sessionId.value
        return NotesSyncEngine(
            // The workspace's own Notes API (its web host is a per-server setting), read at each call so a change applies right away.
            api = NotesApiClient(okHttpClient.withoutBodyLogging(), DefaultNotesTokenSource(client, systemClock), dispatchers) {
                workspaceConfigRepository.cached(client.sessionId)?.links?.notesApi
            },
            store = NotesLocalStore(openDatabase(userId)),
            clock = systemClock,
            dispatchers = dispatchers,
            backgroundScope = client.sessionCoroutineScope,
            attachmentsDir = NotesBindingContainer.attachmentsDir(context, userId),
            pendingDir = NotesBindingContainer.pendingUploadsDir(context, userId),
            photoConverter = photoConverter,
            prefetchPolicy = prefetchPolicy,
            analyticsService = analyticsService,
        )
    }

    /**
     * One encrypted database per account, named after a hash of the user id so the file name doesn't reveal who it belongs to.
     * The key is random and stored beside it, as for Element's own databases.
     */
    private fun openDatabase(userId: String): NotesDatabase {
        val name = NotesBindingContainer.databaseName(userId)
        val secretFile = context.getDatabasePath("$name.key")
        secretFile.parentFile?.mkdirs()
        val driver = SqlCipherDriverFactory(RandomDatabaseSecretProvider(context, secretFile))
            .create(schema = NotesDatabase.Schema, name = "$name.db", context = context)
        return NotesDatabase(driver)
    }

    /**
     * The app's client logs whole requests and answers when debug logging is on. For notes that would mean
     * note text and the OpenID token in logs, so this copy (sharing the same connections) drops that logger.
     */
    private fun OkHttpClient.withoutBodyLogging(): OkHttpClient = newBuilder()
        .apply { interceptors().removeAll { it is DynamicHttpLoggingInterceptor } }
        .build()
}
