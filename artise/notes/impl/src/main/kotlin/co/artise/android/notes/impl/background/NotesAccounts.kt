/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.di.NotesBindingContainer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.flow.first

/** An account whose notes are on this phone: its client (for the chats' room state) and its notes. */
data class NotesAccount(val client: MatrixClient, val repository: NotesRepository)

/** The accounts signed in on this phone that have notes saved on it. */
interface NotesAccounts {
    suspend fun withNotes(): List<SessionId>

    suspend fun open(sessionId: SessionId): NotesAccount?
}

@ContributesBinding(AppScope::class)
class DefaultNotesAccounts(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val matrixClientProvider: MatrixClientProvider,
    private val engines: NotesEngines,
) : NotesAccounts {
    // An account that never opened Notas has no notes database, and isn't given one just to check.
    override suspend fun withNotes(): List<SessionId> = sessionStore.sessionsFlow().first()
        .map { it.userId }
        .filter { context.getDatabasePath(NotesBindingContainer.databaseName(it) + ".db").exists() }
        .map(::SessionId)

    override suspend fun open(sessionId: SessionId): NotesAccount? =
        matrixClientProvider.getOrRestore(sessionId).getOrNull()?.let { client -> NotesAccount(client, engines.forClient(client)) }
}
