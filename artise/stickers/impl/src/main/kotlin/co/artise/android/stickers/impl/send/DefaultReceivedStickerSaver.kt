/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.send

import co.artise.android.stickers.api.ReceivedSticker
import co.artise.android.stickers.api.ReceivedStickerSaver
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import co.artise.android.stickers.impl.packs.StickerRepository
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultReceivedStickerSaver(
    private val repository: StickerRepository,
) : ReceivedStickerSaver {
    override suspend fun save(sticker: ReceivedSticker): Result<Unit> = repository.addUploaded(
        Sticker(
            id = "",
            description = sticker.description,
            source = StickerSource.Uploaded(sticker.mxcUrl),
            width = sticker.width,
            height = sticker.height,
            mimeType = sticker.mimeType,
            size = sticker.size,
        )
    ).map { }
}
