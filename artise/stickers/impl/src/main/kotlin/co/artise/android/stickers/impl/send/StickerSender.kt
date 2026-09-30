/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.send

import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.packs.StickerRepository
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.matrix.api.room.JoinedRoom

/** Sends a sticker to a chat (encrypted when the chat is). */
@Inject
class StickerSender(
    private val repository: StickerRepository,
) {
    suspend fun send(room: JoinedRoom, sticker: Sticker): Result<Unit> = repository.mxcUrlFor(sticker).mapCatchingExceptions { url ->
        room.sendRaw(StickerMessage.EVENT_TYPE, StickerMessage.content(sticker, url)).getOrThrow()
    }
}
