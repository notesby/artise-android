/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.test

import co.artise.android.sounds.api.ChatSoundChoice
import co.artise.android.sounds.api.ChatSoundState
import co.artise.android.sounds.api.ChatSounds
import co.artise.android.sounds.api.SavedSound
import co.artise.android.sounds.api.SoundImportResult
import co.artise.android.sounds.api.SoundLibrary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeChatSounds(
    private val channelId: String? = null,
) : ChatSounds {
    val choices = MutableStateFlow<Map<Pair<SessionId, RoomId>, ChatSoundChoice>>(emptyMap())

    override fun choice(sessionId: SessionId, roomId: RoomId): Flow<ChatSoundChoice> =
        choices.map { it[sessionId to roomId] ?: ChatSoundChoice.Default }

    override suspend fun set(sessionId: SessionId, roomId: RoomId, roomName: String, choice: ChatSoundChoice) {
        choices.value = choices.value + Pair(sessionId to roomId, choice)
    }

    override suspend fun channelIdFor(sessionId: SessionId, roomId: RoomId): String? = channelId

    override suspend fun changedInPhoneSettings(sessionId: SessionId, roomId: RoomId) = false

    override suspend fun clear(sessionId: SessionId) {
        choices.value = choices.value.filterKeys { it.first != sessionId }
    }
}

class FakeSoundLibrary(
    override val isSupported: Boolean = true,
    private val importResult: (String) -> SoundImportResult = { SoundImportResult.Imported(SavedSound("new", "new.m4a", it)) },
) : SoundLibrary {
    override val sounds = MutableStateFlow<List<SavedSound>>(emptyList())

    override suspend fun import(uri: String): SoundImportResult = importResult(uri).also { result ->
        if (result is SoundImportResult.Imported) sounds.value = sounds.value + result.sound
    }

    override suspend fun remove(soundId: String) {
        sounds.value = sounds.value.filterNot { it.id == soundId }
    }
}

fun aChatSoundState(choice: ChatSoundChoice = ChatSoundChoice.Default) = ChatSoundState(
    isSupported = true,
    choice = choice,
    sounds = persistentListOf(),
    changedInPhoneSettings = false,
    isPickerVisible = false,
    importProblem = null,
    eventSink = {},
)
