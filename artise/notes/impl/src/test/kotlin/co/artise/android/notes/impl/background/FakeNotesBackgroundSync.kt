/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import co.artise.android.notes.api.NotesBackgroundSync
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

class FakeNotesBackgroundSync(var allowed: Boolean = true) : NotesBackgroundSync {
    override fun isAllowedNow() = allowed

    override suspend fun syncChat(sessionId: SessionId, roomId: RoomId) = allowed

    override suspend fun syncAll() = Unit
}

class FakeNotesAccounts(private val accounts: Map<SessionId, NotesAccount>) : NotesAccounts {
    override suspend fun withNotes() = accounts.keys.toList()

    override suspend fun open(sessionId: SessionId) = accounts[sessionId]
}
