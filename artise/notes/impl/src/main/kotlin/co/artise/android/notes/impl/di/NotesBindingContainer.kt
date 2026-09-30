/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.di

import android.content.Context
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.attachments.PhotoConverter
import co.artise.android.notes.impl.attachments.PrefetchPolicy
import co.artise.android.notes.impl.auth.NotesTokenSource
import co.artise.android.notes.impl.db.NotesDatabase
import co.artise.android.notes.impl.local.NotesLocalStore
import co.artise.android.notes.impl.remote.NotesApiClient
import co.artise.android.notes.impl.sync.NotesSyncEngine
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.interceptors.DynamicHttpLoggingInterceptor
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.encrypteddb.SqlCipherDriverFactory
import io.element.encrypteddb.passphrase.RandomDatabaseSecretProvider
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

@BindingContainer
@ContributesTo(SessionScope::class)
object NotesBindingContainer {
    /**
     * One encrypted database per account, named after a hash of the user id so the file name
     * doesn't reveal who it belongs to. The key is random and stored beside it, as for Element's own databases.
     */
    @Provides
    @SingleIn(SessionScope::class)
    fun providesNotesDatabase(
        @ApplicationContext context: Context,
        matrixClient: MatrixClient,
    ): NotesDatabase {
        val name = databaseName(matrixClient.sessionId.value)
        val secretFile = context.getDatabasePath("$name.key")
        secretFile.parentFile?.mkdirs()
        val driver = SqlCipherDriverFactory(RandomDatabaseSecretProvider(context, secretFile))
            .create(schema = NotesDatabase.Schema, name = "$name.db", context = context)
        return NotesDatabase(driver)
    }

    @Provides
    @SingleIn(SessionScope::class)
    fun providesNotesRepository(
        @ApplicationContext context: Context,
        matrixClient: MatrixClient,
        okHttpClient: OkHttpClient,
        tokenSource: NotesTokenSource,
        database: NotesDatabase,
        systemClock: SystemClock,
        dispatchers: CoroutineDispatchers,
        @SessionCoroutineScope sessionScope: CoroutineScope,
        photoConverter: PhotoConverter,
        prefetchPolicy: PrefetchPolicy,
    ): NotesRepository = NotesSyncEngine(
        api = NotesApiClient(okHttpClient.withoutBodyLogging(), tokenSource, dispatchers),
        store = NotesLocalStore(database),
        clock = systemClock,
        dispatchers = dispatchers,
        backgroundScope = sessionScope,
        attachmentsDir = attachmentsDir(context, matrixClient.sessionId.value),
        pendingDir = pendingUploadsDir(context, matrixClient.sessionId.value),
        photoConverter = photoConverter,
        prefetchPolicy = prefetchPolicy,
    )

    /** Photos and files waiting to upload: app storage, so Android doesn't clear them like the cache. */
    fun pendingUploadsDir(context: Context, userId: String): File = File(context.filesDir, "notes_pending/" + databaseName(userId))

    /** Downloaded photos and files, in the app's private cache (shared with other apps only one file at a time, when opened). */
    fun attachmentsDir(context: Context, userId: String): File = File(context.cacheDir, "notes_attachments/" + databaseName(userId))

    /**
     * The app's client logs whole requests and answers when debug logging is on. For notes that would mean
     * note text and the OpenID token in logs, so this copy (sharing the same connections) drops that logger.
     */
    private fun OkHttpClient.withoutBodyLogging(): OkHttpClient = newBuilder()
        .apply { interceptors().removeAll { it is DynamicHttpLoggingInterceptor } }
        .build()

    /** The account's database file name, without extension; also used to delete it at sign-out. */
    fun databaseName(userId: String): String = "artise_notes_" + sha256(userId).take(16)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
