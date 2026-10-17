/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import co.artise.android.sounds.api.ChatSoundChoice
import co.artise.android.sounds.api.SavedSound
import co.artise.android.sounds.test.FakeSoundLibrary
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class DefaultChatSoundsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val library = FakeSoundLibrary().apply { sounds.value = listOf(SavedSound("s1", "risa.m4a", "content://media/external/audio/media/1")) }

    /** A chat's own sound gets its own channel with that sound; a new choice replaces the channel; Predeterminado removes it. */
    @Test
    fun `each choice gets its own channel`() = runTest {
        val sounds = DefaultChatSounds(context, library, testCoroutineDispatchers(), FakeStringProvider("Sound: Mamá"))
        assertThat(sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)).isNull()

        sounds.set(A_SESSION_ID, A_ROOM_ID, "Mamá", ChatSoundChoice.Saved("s1"))
        val first = sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)!!
        val channel = notificationManager.getNotificationChannel(first)
        assertThat(channel.sound.toString()).isEqualTo("content://media/external/audio/media/1")
        assertThat(channel.name.toString()).contains("Mamá")
        assertThat(first).doesNotContain(A_ROOM_ID.value)
        assertThat(sounds.choice(A_SESSION_ID, A_ROOM_ID).first()).isEqualTo(ChatSoundChoice.Saved("s1"))
        assertThat(sounds.changedInPhoneSettings(A_SESSION_ID, A_ROOM_ID)).isFalse()

        sounds.set(A_SESSION_ID, A_ROOM_ID, "Mamá", ChatSoundChoice.Silent)
        val second = sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)!!
        assertThat(second).isNotEqualTo(first)
        assertThat(notificationManager.getNotificationChannel(first)).isNull()
        assertThat(notificationManager.getNotificationChannel(second).sound).isNull()

        sounds.set(A_SESSION_ID, A_ROOM_ID, "Mamá", ChatSoundChoice.Default)
        assertThat(sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)).isNull()
        assertThat(notificationManager.getNotificationChannel(second)).isNull()
    }

    /** A removed sound sends its chats back to the app's own sound. */
    @Test
    fun `a removed sound falls back to the default`() = runTest {
        val sounds = DefaultChatSounds(context, library, testCoroutineDispatchers(), FakeStringProvider("Sound: Mamá"))
        sounds.set(A_SESSION_ID, A_ROOM_ID, "Tienda", ChatSoundChoice.Saved("s1"))
        library.remove("s1")
        assertThat(sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)).isNull()
        assertThat(sounds.choice(A_SESSION_ID, A_ROOM_ID).first()).isEqualTo(ChatSoundChoice.Default)
    }

    /** Signing out of a workspace removes only that workspace's chat sounds. */
    @Test
    fun `clearing a workspace keeps the others`() = runTest {
        val sounds = DefaultChatSounds(context, library, testCoroutineDispatchers(), FakeStringProvider("Sound: Mamá"))
        sounds.set(A_SESSION_ID, A_ROOM_ID, "Mamá", ChatSoundChoice.Silent)
        sounds.set(A_SESSION_ID_2, A_ROOM_ID_2, "Tienda", ChatSoundChoice.Silent)
        sounds.clear(A_SESSION_ID)
        assertThat(sounds.channelIdFor(A_SESSION_ID, A_ROOM_ID)).isNull()
        assertThat(sounds.channelIdFor(A_SESSION_ID_2, A_ROOM_ID_2)).isNotNull()
    }
}
