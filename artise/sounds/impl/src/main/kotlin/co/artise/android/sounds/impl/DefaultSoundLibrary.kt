/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.core.net.toUri
import co.artise.android.sounds.api.SavedSound
import co.artise.android.sounds.api.SoundImportResult
import co.artise.android.sounds.api.SoundLibrary
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import kotlin.math.ceil

/**
 * "Tus sonidos": each picked file is copied into the phone's notification sounds (Notifications/Artise), owned by the app, so the
 * system can always play it, even after the original is moved or deleted and after a restart. Needs Android 10 (no storage permission).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultSoundLibrary(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : SoundLibrary {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }
    private val soundsFlow = MutableStateFlow(load())

    override val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override val sounds: StateFlow<List<SavedSound>> = soundsFlow.asStateFlow()

    override suspend fun import(uri: String): SoundImportResult = withContext(dispatchers.io) {
        if (!isSupported) return@withContext SoundImportResult.Failed
        runCatchingExceptions { copyIn(uri.toUri()) }
            .onFailure { Timber.w(it, "Couldn't add a notification sound") }
            .getOrDefault(SoundImportResult.Failed)
    }

    override suspend fun remove(soundId: String) = withContext(dispatchers.io) {
        val sound = soundsFlow.value.firstOrNull { it.id == soundId } ?: return@withContext
        runCatchingExceptions { context.contentResolver.delete(sound.uri.toUri(), null, null) }
            .onFailure { Timber.w(it, "Couldn't delete a notification sound") }
        save(soundsFlow.value - sound)
    }

    private fun copyIn(source: Uri): SoundImportResult {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(source)
        if (mimeType?.startsWith("audio/") != true) return SoundImportResult.NotAudio
        var name = "sound"
        var size = -1L
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        if (size > SoundLibrary.MAX_BYTES) return SoundImportResult.TooBig
        val durationMillis = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, source)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } ?: return SoundImportResult.NotAudio
        if (durationMillis > SoundLibrary.MAX_SECONDS * 1000L) {
            return SoundImportResult.TooLong(ceil(durationMillis / 1000.0).toInt())
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/Artise")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return SoundImportResult.Failed
        val copied = resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        }
        if (copied == null) {
            resolver.delete(target, null, null)
            return SoundImportResult.Failed
        }
        resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        val sound = SavedSound(id = UUID.randomUUID().toString(), name = name, uri = target.toString())
        save(soundsFlow.value + sound)
        return SoundImportResult.Imported(sound)
    }

    private fun load(): List<SavedSound> = runCatchingExceptions {
        preferences.getString(KEY_SOUNDS, null)?.let { json.decodeFromString<List<SoundDto>>(it) }.orEmpty().map { SavedSound(it.id, it.name, it.uri) }
    }.getOrDefault(emptyList())

    private fun save(sounds: List<SavedSound>) {
        preferences.edit { putString(KEY_SOUNDS, json.encodeToString(sounds.map { SoundDto(it.id, it.name, it.uri) })) }
        soundsFlow.value = sounds
    }

    @Serializable
    private data class SoundDto(val id: String, val name: String, val uri: String)

    private companion object {
        const val PREFERENCES = "artise_sounds"
        const val KEY_SOUNDS = "sounds"
    }
}
