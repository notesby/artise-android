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

    /**
     * Syncs one chat's notes in the background, when allowed; false when it was skipped or failed. The silent push the server will send
     * when a chat's notes change is meant to call this.
     */
    suspend fun syncChat(sessionId: SessionId, roomId: RoomId): Boolean

    /** Syncs every chat whose notes are on this phone, in every account, when allowed. */
    suspend fun syncAll()
}
