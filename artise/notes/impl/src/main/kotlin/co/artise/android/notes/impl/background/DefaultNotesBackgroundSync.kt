/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import co.artise.android.notes.api.NotesBackgroundSettings
import co.artise.android.notes.api.NotesBackgroundSync
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

@ContributesBinding(AppScope::class)
class DefaultNotesBackgroundSync(
    private val settings: NotesBackgroundSettings,
    private val connection: NotesConnectionMonitor,
    private val accounts: NotesAccounts,
    private val scheduler: NotesSyncScheduler,
) : NotesBackgroundSync {
    override fun isAllowedNow(): Boolean = settings.network.value.allows(connection.current())

    override suspend fun syncChat(sessionId: SessionId, roomId: RoomId): Boolean {
        if (!isAllowedNow()) return false
        val account = accounts.open(sessionId) ?: return false
        return account.repository.sync(roomId).isSuccess
    }

    override suspend fun syncChatInAnyAccount(roomId: RoomId): Boolean {
        if (!isAllowedNow()) return false
        val holders = accounts.withNotes()
            .mapNotNull { accounts.open(it) }
            .filter { account -> account.repository.cachedChats().any { it.roomId == roomId } }
        return holders.map { it.repository.sync(roomId).isSuccess }.any { it }
    }

    override fun onNotesPush(roomId: RoomId) = scheduler.scheduleChat(roomId, settings.network.value)

    override suspend fun syncAll() {
        for (sessionId in accounts.withNotes()) {
            val account = accounts.open(sessionId) ?: continue
            for (chat in account.repository.cachedChats()) {
                // Checked before each chat: the connection may change while a long list syncs.
                if (!isAllowedNow()) return
                account.repository.sync(chat.roomId)
            }
        }
    }
}
