/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.maker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import co.artise.android.stickers.impl.packs.StickerPicture
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** A sticker made from a photo; [isCutOut] is false when the photo was used whole (the cut-out found nothing). */
@Suppress("UseDataClass")
class MadeSticker(val picture: StickerPicture, val isCutOut: Boolean)

/** Turns a photo into a sticker: the subject cut out, trimmed, at most 512 px, as WebP. */
fun interface StickerMaker {
    suspend fun make(photo: Uri): Result<MadeSticker>
}

@ContributesBinding(AppScope::class)
class DefaultStickerMaker(
    @ApplicationContext private val context: Context,
    private val cutout: SubjectCutout,
    private val dispatchers: CoroutineDispatchers,
) : StickerMaker {
    override suspend fun make(photo: Uri): Result<MadeSticker> = runCatchingExceptions {
        val decoded = withContext(dispatchers.io) { decode(photo) } ?: error("Couldn't read the photo")
        val cutOut = cutout.cutOut(decoded)
        withContext(dispatchers.computation) {
            val box = cutOut?.let { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                StickerGeometry.visibleBox(bitmap.width, bitmap.height, padding = max(bitmap.width, bitmap.height) / PADDING_DIVISOR) { x, y ->
                    pixels[y * bitmap.width + x] ushr ALPHA_SHIFT
                }
            }
            val source = if (box != null) cutOut else decoded
            val area = box ?: StickerGeometry.centerSquare(decoded.width, decoded.height)
            val (width, height) = StickerGeometry.fit(area.width, area.height)
            val cropped = Bitmap.createBitmap(source, area.left, area.top, area.width, area.height)
            val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
            MadeSticker(StickerPicture(encode(scaled), width, height, "image/webp"), isCutOut = box != null)
        }
    }

    /** In ordinary memory (not the graphics hardware), upright, and no bigger than needed for the cut-out. */
    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatchingExceptions {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val side = max(info.size.width, info.size.height)
                    if (side > DECODE_SIDE) {
                        val ratio = DECODE_SIDE.toFloat() / side
                        decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                    }
                }
            }.getOrNull()?.let { return it }
        }
        // Older phones, and photos ImageDecoder can't read (some iPhone HDR photos).
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // BitmapFactory ignores the camera's "which way is up" tag.
        val degrees = runCatchingExceptions { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
        if (degrees == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

    @Suppress("DEPRECATION")
    private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(format, WEBP_QUALITY, out)
        out.toByteArray()
    }

    private companion object {
        /** Big enough for a clean cut-out, small enough to be quick. */
        const val DECODE_SIDE = 1280
        const val WEBP_QUALITY = 85
        const val PADDING_DIVISOR = 40
        const val ALPHA_SHIFT = 24
    }
}
