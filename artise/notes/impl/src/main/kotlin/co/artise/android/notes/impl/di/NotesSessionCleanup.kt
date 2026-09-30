/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.di

import android.app.Activity
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import timber.log.Timber

/**
 * Deletes an account's notes from the phone when it signs out: the encrypted database, its key and its journal.
 * Registered at app start, since sign-out can happen from any screen.
 */
@ContributesIntoSet(AppScope::class)
class NotesSessionCleanup(
    @ApplicationContext private val context: Context,
    private val sessionObserver: SessionObserver,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) = deleteNotes(userId)
        })
    }

    fun deleteNotes(userId: String) {
        val name = NotesBindingContainer.databaseName(userId)
        listOf("$name.db", "$name.db-journal", "$name.db-wal", "$name.db-shm", "$name.key")
            .map { context.getDatabasePath(it) }
            .filter { it.exists() }
            .forEach { file ->
                if (!file.delete()) Timber.w("Couldn't delete a notes file after sign-out")
            }
        // Downloaded photos and files too.
        NotesBindingContainer.attachmentsDir(context, userId).deleteRecursively()
    }
}
