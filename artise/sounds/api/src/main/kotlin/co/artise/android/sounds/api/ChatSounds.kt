/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A sound the person picked from their files, copied into the phone's notification sounds (Notifications/Artise). */
data class SavedSound(
    val id: String,
    val name: String,
    val uri: String,
)

/** What a chat's notifications sound like. */
@Immutable
sealed interface ChatSoundChoice {
    /** The app's sound, from Ajustes → Notificaciones. */
    data object Default : ChatSoundChoice

    /** The notification shows without a sound. */
    data object Silent : ChatSoundChoice

    data class Saved(val soundId: String) : ChatSoundChoice

    /** One of the phone's own sounds, from the system picker. */
    data class Phone(val uri: String) : ChatSoundChoice
}

@Immutable
sealed interface SoundImportResult {
    data class Imported(val sound: SavedSound) : SoundImportResult

    /** Longer than [SoundLibrary.MAX_SECONDS]; [seconds] is the file's length. */
    data class TooLong(val seconds: Int) : SoundImportResult

    data object TooBig : SoundImportResult

    data object NotAudio : SoundImportResult

    data object Failed : SoundImportResult
}

/** The person's own sounds ("Tus sonidos"). Kept on this phone only. */
interface SoundLibrary {
    /** False below Android 10, where an app can't add a notification sound without a storage permission. */
    val isSupported: Boolean

    val sounds: StateFlow<List<SavedSound>>

    /** Copies the picked file (a content address from the document picker) into the phone's notification sounds. */
    suspend fun import(uri: String): SoundImportResult

    /** Removes the sound; chats using it go back to [ChatSoundChoice.Default]. */
    suspend fun remove(soundId: String)

    companion object {
        const val MAX_SECONDS = 30
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}

/** Each chat's sound, as its own notification channel. Per account and chat; signing out of an account removes its chats' channels. */
interface ChatSounds {
    fun choice(sessionId: SessionId, roomId: RoomId): Flow<ChatSoundChoice>

    suspend fun set(sessionId: SessionId, roomId: RoomId, roomName: String, choice: ChatSoundChoice)

    /** The channel to post this chat's sounding notifications on, or null to use the app's own. */
    suspend fun channelIdFor(sessionId: SessionId, roomId: RoomId): String?

    /** True when the chat's channel sound was changed in the phone's settings, which then wins. */
    suspend fun changedInPhoneSettings(sessionId: SessionId, roomId: RoomId): Boolean

    suspend fun clear(sessionId: SessionId)
}
