/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import co.artise.android.stickers.impl.maker.MadeSticker
import co.artise.android.stickers.impl.maker.StickerMaker
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.launch

data class AddStickerState(
    val step: AddStickerStep,
    /** The last save didn't work: say so, and let the person try again. */
    val saveFailed: Boolean,
    val eventSink: (AddStickerEvent) -> Unit,
)

@Immutable
sealed interface AddStickerStep {
    data object Preparing : AddStickerStep

    /** Nobody is signed in on this phone. */
    data object SignedOut : AddStickerStep

    data object Failed : AddStickerStep

    data class Ready(val made: MadeSticker, val isSaving: Boolean) : AddStickerStep

    data object Saved : AddStickerStep
}

sealed interface AddStickerEvent {
    data object Save : AddStickerEvent
}

/**
 * "Add to my stickers" from another app's share menu: the picture is made into a sticker (cut out when it's a photo,
 * as it is when its background is already transparent) and saved to the signed-in account's stickers.
 */
@AssistedInject
class AddStickerPresenter(
    @Assisted private val uri: String,
    private val account: StickerAccount,
    private val maker: StickerMaker,
) : Presenter<AddStickerState> {
    @AssistedFactory
    fun interface Factory {
        fun create(uri: String): AddStickerPresenter
    }

    @Composable
    override fun present(): AddStickerState {
        val scope = rememberCoroutineScope()
        var step by remember { mutableStateOf<AddStickerStep>(AddStickerStep.Preparing) }
        var saveFailed by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            step = if (!account.isSignedIn()) {
                AddStickerStep.SignedOut
            } else {
                maker.make(uri.toUri()).fold(
                    onSuccess = { AddStickerStep.Ready(it, isSaving = false) },
                    onFailure = { AddStickerStep.Failed },
                )
            }
        }

        return AddStickerState(
            step = step,
            saveFailed = saveFailed,
            eventSink = { event ->
                when (event) {
                    AddStickerEvent.Save -> (step as? AddStickerStep.Ready)?.takeIf { !it.isSaving }?.let { ready ->
                        step = ready.copy(isSaving = true)
                        saveFailed = false
                        scope.launch {
                            account.addToMyStickers(ready.made)
                                .onSuccess { step = AddStickerStep.Saved }
                                .onFailure {
                                    step = ready
                                    saveFailed = true
                                }
                        }
                    }
                }
            },
        )
    }
}
