/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.api

import android.net.Uri
import androidx.compose.runtime.Composable
import io.element.android.libraries.matrix.api.room.JoinedRoom

/** The sticker picker, opened from the composer's sticker button: tapping a sticker sends it to [room]. */
interface StickerPickerRenderer {
    @Composable
    fun Render(room: JoinedRoom, onDismiss: () -> Unit)
}

/** Sends pictures that keyboards (Gboard, Samsung Keyboard and the sticker apps built on them) insert, as stickers. */
fun interface KeyboardStickerSender {
    /**
     * Sends the picture at [uri] as a sticker when it is one (a small WebP, PNG or GIF), and says so; a photo returns
     * false and goes the usual way.
     */
    suspend fun trySend(room: JoinedRoom, uri: Uri): Boolean
}

/** A sticker someone sent, already on the server: saving it to your stickers reuses the same picture. */
data class ReceivedSticker(
    val mxcUrl: String,
    val description: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val size: Long,
)

/** "Save to my stickers" on a sticker in a chat. */
fun interface ReceivedStickerSaver {
    suspend fun save(sticker: ReceivedSticker): Result<Unit>
}
