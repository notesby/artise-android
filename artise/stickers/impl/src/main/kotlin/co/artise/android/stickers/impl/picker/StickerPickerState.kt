/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.picker

import androidx.compose.runtime.Immutable
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.maker.MadeSticker
import kotlinx.collections.immutable.ImmutableList

data class StickerPickerState(
    val tab: StickerTab,
    val mine: ImmutableList<Sticker>,
    val starter: ImmutableList<Sticker>,
    /** The sticker being sent right now: it shows a spinner, and other taps wait. */
    val sendingId: String?,
    val making: MakingState,
    val removing: Sticker?,
    val problem: StickerProblem?,
    val eventSink: (StickerPickerEvent) -> Unit,
)

enum class StickerTab {
    MINE,
    STARTER,
}

/** Making a sticker from a photo: cutting it out, showing it, saving it. */
@Immutable
sealed interface MakingState {
    data object Idle : MakingState

    data object Working : MakingState

    data class Ready(val made: MadeSticker) : MakingState

    data class Saving(val made: MadeSticker) : MakingState
}

enum class StickerProblem {
    SEND_FAILED,
    MAKE_FAILED,
    SAVE_FAILED,
    REMOVE_FAILED,
}

sealed interface StickerPickerEvent {
    data class SelectTab(val tab: StickerTab) : StickerPickerEvent

    data class Send(val sticker: Sticker) : StickerPickerEvent

    /** A photo was picked ([uri] from the Android photo picker): make a sticker from it. */
    data class Make(val uri: String) : StickerPickerEvent

    /** Keep the sticker just made, and send it too when [andSend]. */
    data class SaveMade(val andSend: Boolean) : StickerPickerEvent

    data object CancelMade : StickerPickerEvent

    /** Long press on one of the person's own stickers: ask before removing it. */
    data class StartRemove(val sticker: Sticker) : StickerPickerEvent

    data object ConfirmRemove : StickerPickerEvent

    data object CancelRemove : StickerPickerEvent

    data object DismissProblem : StickerPickerEvent
}
