/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.di

import android.content.Context
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.background.NotesEngines
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import java.io.File
import java.security.MessageDigest

@BindingContainer
@ContributesTo(SessionScope::class)
object NotesBindingContainer {
    /** The account's notes, shared with the background updates (see [NotesEngines]). */
    @Provides
    fun providesNotesRepository(engines: NotesEngines, matrixClient: MatrixClient): NotesRepository = engines.forClient(matrixClient)

    /** Photos and files waiting to upload: app storage, so Android doesn't clear them like the cache. */
    fun pendingUploadsDir(context: Context, userId: String): File = File(context.filesDir, "notes_pending/" + databaseName(userId))

    /** Downloaded photos and files, in the app's private cache (shared with other apps only one file at a time, when opened). */
    fun attachmentsDir(context: Context, userId: String): File = File(context.cacheDir, "notes_attachments/" + databaseName(userId))

    /** The account's database file name, without extension; also used to delete it at sign-out. */
    fun databaseName(userId: String): String = "artise_notes_" + sha256(userId).take(16)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
