/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.api

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/** The "Sonido" row in a chat's notification settings, and its picker. Presented in the chat's scope. */
@Immutable
data class ChatSoundState(
    val isSupported: Boolean,
    val choice: ChatSoundChoice,
    val sounds: ImmutableList<SavedSound>,
    val changedInPhoneSettings: Boolean,
    val isPickerVisible: Boolean,
    val importProblem: SoundImportResult?,
    val eventSink: (ChatSoundEvent) -> Unit,
)

sealed interface ChatSoundEvent {
    data object OpenPicker : ChatSoundEvent

    data object ClosePicker : ChatSoundEvent

    data class Choose(val choice: ChatSoundChoice) : ChatSoundEvent

    /** A file from the document picker: imported, then chosen. */
    data class Import(val uri: String) : ChatSoundEvent

    data class Remove(val soundId: String) : ChatSoundEvent

    data object ClearProblem : ChatSoundEvent
}
