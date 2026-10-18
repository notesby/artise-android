/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.matrix.api.core.RoomId

/** "Guardar en Notas" on a message: the text becomes a note in that chat's Notas, saying who wrote it and when. */
fun interface MessageNoteSaver {
    /** Returns the new note's path. */
    suspend fun save(roomId: RoomId, senderName: String, sentAtMillis: Long, text: String): Result<String>
}
