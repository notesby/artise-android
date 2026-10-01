/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.picker

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import kotlinx.collections.immutable.persistentListOf

class StickerPickerStatePreviewParam : PreviewParameterProvider<StickerPickerState> {
    override val values = sequenceOf(
        aStickerPickerState(tab = StickerTab.STARTER),
        aStickerPickerState(tab = StickerTab.MINE, mine = persistentListOf()),
        aStickerPickerState(tab = StickerTab.MINE, sendingId = "sticker_1"),
    )
}

private fun aSticker(id: String) = Sticker(
    id = id,
    description = "Sticker",
    source = StickerSource.Starter("stickers/starter/sparkles.webp"),
    width = 256,
    height = 256,
    mimeType = "image/webp",
    size = 1000,
)

internal fun aStickerPickerState(
    tab: StickerTab,
    mine: kotlinx.collections.immutable.ImmutableList<Sticker> = persistentListOf(aSticker("sticker_1"), aSticker("sticker_2")),
    sendingId: String? = null,
) = StickerPickerState(
    tab = tab,
    mine = mine,
    starter = persistentListOf(aSticker("starter:a"), aSticker("starter:b"), aSticker("starter:c")),
    sendingId = sendingId,
    making = MakingState.Idle,
    removing = null,
    problem = null,
    eventSink = {},
)
