/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl

import androidx.compose.runtime.Immutable

/** A sticker in the picker. [source] says where its picture is. */
data class Sticker(
    val id: String,
    /** Read aloud by screen readers and shown by apps that can't draw stickers. */
    val description: String,
    val source: StickerSource,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val size: Long,
)

@Immutable
sealed interface StickerSource {
    /** On the server already: the person's own stickers. */
    data class Uploaded(val mxcUrl: String) : StickerSource

    /** In the app (the starter pack): uploaded the first time it's sent. */
    data class Starter(val assetPath: String) : StickerSource
}
