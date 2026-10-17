/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import co.artise.android.sounds.api.ChatSoundChoice
import co.artise.android.sounds.api.ChatSoundEvent
import co.artise.android.sounds.api.SavedSound
import co.artise.android.sounds.api.SoundImportResult
import co.artise.android.sounds.test.FakeChatSounds
import co.artise.android.sounds.test.FakeSoundLibrary
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatSoundPresenterTest {
    private val chatSounds = FakeChatSounds()

    private fun createChatSoundPresenter(library: FakeSoundLibrary = FakeSoundLibrary()) =
        ChatSoundPresenter(FakeJoinedRoom(), chatSounds, library)

    /** Picking a file adds it to "Tus sonidos" and chooses it for this chat. */
    @Test
    fun `an imported file becomes the chat's sound`() = runTest {
        val library = FakeSoundLibrary()
        createChatSoundPresenter(library).test {
            val initial = awaitItem()
            assertThat(initial.choice).isEqualTo(ChatSoundChoice.Default)
            initial.eventSink(ChatSoundEvent.OpenPicker)
            awaitItem().eventSink(ChatSoundEvent.Import("content://downloads/risa.m4a"))
            val chosen = expectMostRecentItem()
            assertThat(chatSounds.choices.value[A_SESSION_ID to A_ROOM_ID]).isEqualTo(ChatSoundChoice.Saved("new"))
            assertThat(library.sounds.value.map { it.id }).containsExactly("new")
            assertThat(chosen.isPickerVisible).isFalse()
        }
    }

    /** A file over 30 seconds isn't added, and the person is told why. */
    @Test
    fun `a long file is refused`() = runTest {
        createChatSoundPresenter(FakeSoundLibrary(importResult = { SoundImportResult.TooLong(120) })).test {
            awaitItem().eventSink(ChatSoundEvent.Import("content://downloads/song.mp3"))
            assertThat(expectMostRecentItem().importProblem).isEqualTo(SoundImportResult.TooLong(120))
            assertThat(chatSounds.choices.value).isEmpty()
        }
    }

    /** Removing the sound this chat uses puts the chat back on Predeterminado. */
    @Test
    fun `removing the chat's sound goes back to the default`() = runTest {
        val library = FakeSoundLibrary().apply { sounds.value = listOf(SavedSound("s1", "timbre.mp3", "content://media/1")) }
        chatSounds.set(A_SESSION_ID, A_ROOM_ID, "Tienda", ChatSoundChoice.Saved("s1"))
        createChatSoundPresenter(library).test {
            val state = expectMostRecentItem()
            assertThat(state.choice).isEqualTo(ChatSoundChoice.Saved("s1"))
            state.eventSink(ChatSoundEvent.Remove("s1"))
            assertThat(expectMostRecentItem().choice).isEqualTo(ChatSoundChoice.Default)
            assertThat(library.sounds.value).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
