/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.send

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import co.artise.android.stickers.api.KeyboardStickerSender
import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesBinding(SessionScope::class)
class DefaultKeyboardStickerSender(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
) : KeyboardStickerSender {
    override suspend fun trySend(room: JoinedRoom, uri: Uri): Boolean {
        val picture = withContext(dispatchers.io) {
            runCatchingExceptions {
                val mimeType = context.contentResolver.getType(uri)
                if (!KeyboardStickers.isStickerType(mimeType)) return@runCatchingExceptions null
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatchingExceptions null
                if (!KeyboardStickers.isStickerSize(bytes.size.toLong())) return@runCatchingExceptions null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                Triple(bytes, mimeType.orEmpty(), bounds)
            }.getOrNull()
        } ?: return false
        val (bytes, mimeType, bounds) = picture
        val sent = matrixClient.uploadMedia(mimeType, bytes).mapCatchingExceptions { url ->
            val sticker = Sticker(
                id = "",
                description = KeyboardStickers.DESCRIPTION,
                source = StickerSource.Uploaded(url),
                width = bounds.outWidth.coerceAtLeast(1),
                height = bounds.outHeight.coerceAtLeast(1),
                mimeType = mimeType,
                size = bytes.size.toLong(),
            )
            room.sendRaw(StickerMessage.EVENT_TYPE, StickerMessage.content(sticker, url)).getOrThrow()
        }
        // If it can't go as a sticker, it goes the usual way (as a picture, with the usual retry and errors).
        sent.onFailure { Timber.w(it, "Stickers: couldn't send a keyboard sticker, sending it as a picture") }
        return sent.isSuccess
    }
}

/** What reads as a sticker when a keyboard inserts a picture: the formats sticker keyboards use, and small. */
internal object KeyboardStickers {
    const val MAX_BYTES = 1024L * 1024
    const val DESCRIPTION = "Sticker"

    private val types = setOf("image/webp", "image/png", "image/gif")

    fun isStickerType(mimeType: String?): Boolean = mimeType?.lowercase() in types

    fun isStickerSize(size: Long): Boolean = size in 1..MAX_BYTES
}
