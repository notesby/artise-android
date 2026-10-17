/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.net.toUri
import co.artise.android.sounds.api.ChatSoundChoice
import co.artise.android.sounds.api.ChatSounds
import co.artise.android.sounds.api.R
import co.artise.android.sounds.api.SoundLibrary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Each chat with its own sound gets its own notification channel in the "Sonidos de chats" group: a channel's sound can't change, so a
 * new choice deletes the channel and creates it again under a new id, as Element does for its own channel. Choices are kept on this
 * phone only.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultChatSounds(
    @ApplicationContext private val context: Context,
    private val soundLibrary: SoundLibrary,
    private val dispatchers: CoroutineDispatchers,
    private val stringProvider: StringProvider,
) : ChatSounds {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }
    private val json = Json { ignoreUnknownKeys = true }
    private val entries by lazy { MutableStateFlow(loadAll()) }

    override fun choice(sessionId: SessionId, roomId: RoomId): Flow<ChatSoundChoice> =
        entries.map { it[key(sessionId, roomId)]?.toChoice() ?: ChatSoundChoice.Default }

    override suspend fun set(sessionId: SessionId, roomId: RoomId, roomName: String, choice: ChatSoundChoice) = withContext<Unit>(dispatchers.io) {
        val key = key(sessionId, roomId)
        val previous = entries.value[key]
        previous?.let { deleteChannel(it.channelId) }
        val next = when (choice) {
            ChatSoundChoice.Default -> null
            else -> Entry.of(choice, version = (previous?.version ?: 0) + 1, roomName = roomName, hash = hash(key))
        }
        update(key, next)
        if (next != null) ensureChannel(next)
    }

    override suspend fun channelIdFor(sessionId: SessionId, roomId: RoomId): String? = withContext(dispatchers.io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext null
        val key = key(sessionId, roomId)
        val entry = entries.value[key] ?: return@withContext null
        // A removed sound: back to the app's own.
        if (entry.kind == KIND_SAVED && soundLibrary.sounds.value.none { it.id == entry.value }) {
            deleteChannel(entry.channelId)
            update(key, null)
            return@withContext null
        }
        ensureChannel(entry)
        entry.channelId
    }

    override suspend fun changedInPhoneSettings(sessionId: SessionId, roomId: RoomId): Boolean = withContext(dispatchers.io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext false
        val entry = entries.value[key(sessionId, roomId)] ?: return@withContext false
        val channel = notificationManager.getNotificationChannel(entry.channelId) ?: return@withContext false
        channel.sound?.toString() != soundUri(entry)
    }

    override suspend fun clear(sessionId: SessionId) = withContext(dispatchers.io) {
        entries.value.filterKeys { it.startsWith(sessionId.value + SEPARATOR) }.forEach { (key, entry) ->
            deleteChannel(entry.channelId)
            update(key, null)
        }
    }

    private fun soundUri(entry: Entry): String? = when (entry.kind) {
        KIND_SAVED -> soundLibrary.sounds.value.firstOrNull { it.id == entry.value }?.uri
        KIND_PHONE -> entry.value
        else -> null
    }

    private fun ensureChannel(entry: Entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(entry.channelId) != null) return
        createChannel(entry)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(entry: Entry) {
        notificationManager.createNotificationChannelGroup(NotificationChannelGroup(GROUP, stringProvider.getString(R.string.artise_sound_channel_group)))
        val channel = NotificationChannel(
            entry.channelId,
            stringProvider.getString(R.string.artise_sound_channel_name, entry.roomName),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            group = GROUP
            enableVibration(true)
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(soundUri(entry)?.toUri(), attributes.takeIf { soundUri(entry) != null })
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun deleteChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.deleteNotificationChannel(channelId)
    }

    private fun update(key: String, entry: Entry?) {
        val next = if (entry == null) entries.value - key else entries.value + (key to entry)
        preferences.edit { putString(KEY_ENTRIES, json.encodeToString(next)) }
        entries.value = next
    }

    private fun loadAll(): Map<String, Entry> = preferences.getString(KEY_ENTRIES, null)
        ?.let { runCatchingExceptions { json.decodeFromString<Map<String, Entry>>(it) }.getOrNull() }
        .orEmpty()

    private fun key(sessionId: SessionId, roomId: RoomId) = sessionId.value + SEPARATOR + roomId.value

    @Serializable
    internal data class Entry(
        val kind: String,
        val value: String? = null,
        val version: Int,
        val roomName: String,
        val hash: String,
    ) {
        val channelId: String get() = "artise_chat_${hash}_v$version"

        fun toChoice(): ChatSoundChoice = when (kind) {
            KIND_SILENT -> ChatSoundChoice.Silent
            KIND_SAVED -> ChatSoundChoice.Saved(value.orEmpty())
            KIND_PHONE -> ChatSoundChoice.Phone(value.orEmpty())
            else -> ChatSoundChoice.Default
        }

        companion object {
            fun of(choice: ChatSoundChoice, version: Int, roomName: String, hash: String) = when (choice) {
                ChatSoundChoice.Silent -> Entry(KIND_SILENT, null, version, roomName, hash)
                is ChatSoundChoice.Saved -> Entry(KIND_SAVED, choice.soundId, version, roomName, hash)
                is ChatSoundChoice.Phone -> Entry(KIND_PHONE, choice.uri, version, roomName, hash)
                ChatSoundChoice.Default -> error("The default sound has no channel")
            }
        }
    }

    internal companion object {
        const val PREFERENCES = "artise_chat_sounds"
        const val KEY_ENTRIES = "entries"
        const val GROUP = "artise_chat_sounds"
        const val SEPARATOR = "|"
        const val KIND_SILENT = "silent"
        const val KIND_SAVED = "saved"
        const val KIND_PHONE = "phone"

        // The chat isn't written in the channel id in clear.
        fun hash(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}
