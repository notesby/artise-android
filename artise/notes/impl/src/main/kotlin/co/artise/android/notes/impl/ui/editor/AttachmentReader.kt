/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import co.artise.android.notes.impl.attachments.PhotoConverter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext

/**
 * A photo or file the person picked, read into memory for uploading.
 * Not a data class: its equality would compare the bytes by reference, which is misleading.
 */
@Suppress("UseDataClass")
class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/** Why a picked file can't be attached before even trying to upload it. */
class AttachmentTooBigException : Exception("Over the 50 MB limit")

/** Reads what the Android picker returned; a seam so the editor can be tested without a phone. */
fun interface AttachmentReader {
    suspend fun read(uri: String): Result<PickedFile>

    companion object {
        /** The Notes API's limit for a file. */
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}

@ContributesBinding(AppScope::class)
class DefaultAttachmentReader(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val photoConverter: PhotoConverter,
) : AttachmentReader {
    override suspend fun read(uri: String): Result<PickedFile> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val androidUri = uri.toUri()
            var name = "archivo"
            var size = -1L
            context.contentResolver.query(androidUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
            // Refuse early rather than read 300 MB of video into memory.
            if (size > AttachmentReader.MAX_BYTES) throw AttachmentTooBigException()
            val bytes = context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() } ?: error("Couldn't read the file")
            if (bytes.size > AttachmentReader.MAX_BYTES) throw AttachmentTooBigException()
            val mimeType = context.contentResolver.getType(androidUri) ?: PhotoConverter.mimeTypeOf(name)
            // HEIC and big photos become JPEGs every app can show.
            photoConverter.toJpeg(bytes, mimeType)
                ?.let { jpeg -> PickedFile(PhotoConverter.jpegName(name), "image/jpeg", jpeg) }
                ?: PickedFile(name, mimeType, bytes)
        }
    }
}
