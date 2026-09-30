/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.maker.StickerMaker
import co.artise.android.stickers.impl.packs.StickerRepository
import co.artise.android.stickers.impl.send.StickerSender
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * The sticker picker: the person's own stickers and the starter pack. Tapping one sends it to [room] and closes the
 * picker ([onSent]); "+" makes a new one from a photo.
 */
@AssistedInject
class StickerPickerPresenter(
    @Assisted private val room: JoinedRoom,
    @Assisted private val language: String,
    @Assisted private val onSent: () -> Unit,
    private val repository: StickerRepository,
    private val sender: StickerSender,
    private val maker: StickerMaker,
) : Presenter<StickerPickerState> {
    @AssistedFactory
    fun interface Factory {
        fun create(room: JoinedRoom, language: String, onSent: () -> Unit): StickerPickerPresenter
    }

    @Composable
    override fun present(): StickerPickerState {
        val scope = rememberCoroutineScope()
        val mine by repository.myStickers.collectAsState()
        val starter = remember(language) { repository.starterStickers(language).toImmutableList() }
        // The person's own stickers first, once they have some.
        var chosenTab by remember { mutableStateOf<StickerTab?>(null) }
        var sendingId by remember { mutableStateOf<String?>(null) }
        var making by remember { mutableStateOf<MakingState>(MakingState.Idle) }
        var removing by remember { mutableStateOf<Sticker?>(null) }
        var problem by remember { mutableStateOf<StickerProblem?>(null) }

        LaunchedEffect(Unit) { repository.refresh() }

        fun send(sticker: Sticker) {
            if (sendingId != null) return
            sendingId = sticker.id
            scope.launch {
                sender.send(room, sticker)
                    .onSuccess { onSent() }
                    .onFailure { problem = StickerProblem.SEND_FAILED }
                sendingId = null
            }
        }

        return StickerPickerState(
            tab = chosenTab ?: if (mine.isEmpty()) StickerTab.STARTER else StickerTab.MINE,
            mine = mine.toImmutableList(),
            starter = starter,
            sendingId = sendingId,
            making = making,
            removing = removing,
            problem = problem,
            eventSink = { event ->
                when (event) {
                    is StickerPickerEvent.SelectTab -> chosenTab = event.tab
                    is StickerPickerEvent.Send -> send(event.sticker)
                    is StickerPickerEvent.Make -> if (making == MakingState.Idle) {
                        making = MakingState.Working
                        scope.launch {
                            maker.make(event.uri.toUri())
                                .onSuccess { making = MakingState.Ready(it) }
                                .onFailure {
                                    making = MakingState.Idle
                                    problem = StickerProblem.MAKE_FAILED
                                }
                        }
                    }
                    is StickerPickerEvent.SaveMade -> (making as? MakingState.Ready)?.let { ready ->
                        making = MakingState.Saving(ready.made)
                        scope.launch {
                            repository.addToMine(ready.made.picture, STICKER_DESCRIPTION)
                                .onSuccess { saved ->
                                    making = MakingState.Idle
                                    chosenTab = StickerTab.MINE
                                    if (event.andSend) send(saved)
                                }
                                .onFailure {
                                    making = ready
                                    problem = StickerProblem.SAVE_FAILED
                                }
                        }
                    }
                    StickerPickerEvent.CancelMade -> if (making is MakingState.Ready) making = MakingState.Idle
                    is StickerPickerEvent.StartRemove -> removing = event.sticker
                    StickerPickerEvent.CancelRemove -> removing = null
                    StickerPickerEvent.ConfirmRemove -> removing?.let { sticker ->
                        removing = null
                        scope.launch { repository.remove(sticker).onFailure { problem = StickerProblem.REMOVE_FAILED } }
                    }
                    StickerPickerEvent.DismissProblem -> problem = null
                }
            },
        )
    }

    private companion object {
        /** What apps that can't draw stickers show instead, and what screen readers say. */
        const val STICKER_DESCRIPTION = "Sticker"
    }
}
