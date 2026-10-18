/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.sounds.api.ChatSoundChoice
import co.artise.android.sounds.api.ChatSoundEvent
import co.artise.android.sounds.api.ChatSoundState
import co.artise.android.sounds.api.ChatSounds
import co.artise.android.sounds.api.SoundImportResult
import co.artise.android.sounds.api.SoundLibrary
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** The "Sonido" row of the chat on screen. */
@Inject
class ChatSoundPresenter(
    private val room: JoinedRoom,
    private val chatSounds: ChatSounds,
    private val soundLibrary: SoundLibrary,
) : Presenter<ChatSoundState> {
    @Composable
    override fun present(): ChatSoundState {
        val scope = rememberCoroutineScope()
        val choiceFlow = remember { chatSounds.choice(room.sessionId, room.roomId) }
        val choice by choiceFlow.collectAsState(ChatSoundChoice.Default)
        val sounds by soundLibrary.sounds.collectAsState()
        var isPickerVisible by remember { mutableStateOf(false) }
        var importProblem by remember { mutableStateOf<SoundImportResult?>(null) }
        val changedInPhoneSettings by produceState(false, choice) { value = chatSounds.changedInPhoneSettings(room.sessionId, room.roomId) }

        fun choose(next: ChatSoundChoice) = scope.launch {
            chatSounds.set(room.sessionId, room.roomId, room.info().name ?: room.roomId.value, next)
            isPickerVisible = false
        }

        return ChatSoundState(
            isSupported = soundLibrary.isSupported,
            choice = choice,
            sounds = sounds.toImmutableList(),
            changedInPhoneSettings = changedInPhoneSettings,
            isPickerVisible = isPickerVisible,
            importProblem = importProblem,
            eventSink = { event ->
                when (event) {
                    ChatSoundEvent.OpenPicker -> isPickerVisible = true
                    ChatSoundEvent.ClosePicker -> isPickerVisible = false
                    is ChatSoundEvent.Choose -> choose(event.choice)
                    is ChatSoundEvent.Import -> scope.launch {
                        when (val result = soundLibrary.import(event.uri)) {
                            is SoundImportResult.Imported -> choose(ChatSoundChoice.Saved(result.sound.id))
                            else -> importProblem = result
                        }
                    }
                    is ChatSoundEvent.Remove -> scope.launch {
                        if (choice ==
                            ChatSoundChoice.Saved(event.soundId)) {
                                chatSounds.set(room.sessionId, room.roomId, room.info().name.orEmpty(), ChatSoundChoice.Default)
                            }
                        soundLibrary.remove(event.soundId)
                    }
                    ChatSoundEvent.ClearProblem -> importProblem = null
                }
            },
        )
    }
}
