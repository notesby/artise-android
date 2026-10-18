/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.StateFlow

/** Which connections notes may update on while Notas isn't open: Settings → "Actualizar notas en segundo plano". */
enum class NotesBackgroundNetwork { WIFI_ONLY, WIFI_AND_MOBILE }

/**
 * Per phone, for every account. "Solo con Wi-Fi" by default, since many families are on prepaid data. Opening Notas, coming back to the
 * app and sending the person's own edits don't follow it.
 */
interface NotesBackgroundSettings {
    val network: StateFlow<NotesBackgroundNetwork>

    fun set(network: NotesBackgroundNetwork)
}

/** Background updates of notes, gated by [NotesBackgroundSettings] and Data Saver. */
interface NotesBackgroundSync {
    /** Whether the current connection may be used for background updates now. */
    fun isAllowedNow(): Boolean

    /** Syncs one chat's notes in [sessionId] in the background, when allowed; false when it was skipped or failed. */
    suspend fun syncChat(sessionId: SessionId, roomId: RoomId): Boolean

    /**
     * Syncs [roomId]'s notes in whichever account has them on this phone, when allowed; false when none did. The push doesn't say
     * which account, and a room id belongs to one workspace's server.
     */
    suspend fun syncChatInAnyAccount(roomId: RoomId): Boolean

    /**
     * The server's silent push said [roomId]'s notes changed: when that chat's notes are on this phone, queues a background sync that
     * waits for the connection the setting allows, and returns true; otherwise ignores it. Safe to call while the app is closed.
     */
    suspend fun onNotesPush(roomId: RoomId): Boolean

    /** Syncs every chat whose notes are on this phone, in every account, when allowed. */
    suspend fun syncAll()
}

/**
 * The notes push, an FCM data message without a notification: `{"artise_notes_room_id": "!…", "event_id": "$…", "prio": "normal"}`.
 * Checked before the message parser, so it's never treated as a message. `event_id` is the state update's and says nothing about
 * the notes.
 */
object NotesPush {
    const val ROOM_ID_KEY = "artise_notes_room_id"

    /** The chat whose notes changed, or null when [data] isn't a notes push. */
    fun roomIdOf(data: Map<String, String>): RoomId? = data[ROOM_ID_KEY]?.takeIf { it.startsWith("!") }?.let(::RoomId)
}
