/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Turns phone photos into JPEGs every app can show, before they're added to a note.
 *
 * Many phones save HEIC photos (often HDR, with a gain map): web browsers and Obsidian on computers can't show
 * them, some decoders draw them black, and they're large. Big JPEG, PNG and WebP photos are made smaller too.
 */
fun interface PhotoConverter {
    /** A JPEG version of [bytes], or `null` to keep the file as it is (not a photo, a GIF, or already small). */
    fun toJpeg(bytes: ByteArray, mimeType: String): ByteArray?

    companion object {
        /** The long side of a converted photo: sharp on any phone or laptop screen. */
        const val MAX_SIDE = 2560
        const val JPEG_QUALITY = 85

        /** Photos in these formats are always converted: few apps can show them. */
        val ALWAYS = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence", "image/avif")

        /** Photos in these formats are converted only when big. */
        val WHEN_BIG = setOf("image/jpeg", "image/png", "image/webp")
        const val BIG_BYTES = 3L * 1024 * 1024

        /** "IMG_1234.heic" → "IMG_1234.jpg". */
        fun jpegName(name: String): String = name.substringBeforeLast('.', name) + ".jpg"

        /** The type written for a file name, for converting files queued before their type was known. */
        fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

@ContributesBinding(AppScope::class)
class DefaultPhotoConverter : PhotoConverter {
    override fun toJpeg(bytes: ByteArray, mimeType: String): ByteArray? {
        val type = mimeType.lowercase()
        val always = type in PhotoConverter.ALWAYS
        if (!always && type !in PhotoConverter.WHEN_BIG) return null
        val bitmap = decode(bytes) ?: return null
        val tooLarge = max(bitmap.width, bitmap.height) > PhotoConverter.MAX_SIDE
        // A small, already common photo stays exactly as it was.
        if (!always && !tooLarge && bytes.size <= PhotoConverter.BIG_BYTES) return null
        val scaled = scaleDown(bitmap)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, PhotoConverter.JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /**
     * Decodes in memory the phone's graphics hardware doesn't hold, so HDR photos come out as a normal picture.
     * ImageDecoder (Android 9+) reads HEIC and turns the photo upright; older phones don't have HEIC photos.
     * Some iPhone HEICs (HDR gain map, portrait mattes) trip ImageDecoder on some phones: BitmapFactory is tried next.
     */
    private fun decode(bytes: ByteArray): Bitmap? {
        val viaImageDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatchingExceptions {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val side = max(info.size.width, info.size.height)
                    if (side > PhotoConverter.MAX_SIDE) {
                        val ratio = PhotoConverter.MAX_SIDE.toFloat() / side
                        decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                    }
                }
            }.onFailure { Timber.w(it, "Notes: ImageDecoder couldn't read a photo") }.getOrNull()
        } else {
            null
        }
        viaImageDecoder?.let {
            Timber.d("Notes: photo decoded by ImageDecoder, %dx%d %s", it.width, it.height, it.config)
            return it
        }
        return runCatchingExceptions { decodeWithBitmapFactory(bytes) }
            .onFailure { Timber.w(it, "Notes: BitmapFactory couldn't read a photo") }
            .getOrNull()
            ?.also { Timber.d("Notes: photo decoded by BitmapFactory, %dx%d %s", it.width, it.height, it.config) }
    }

    private fun decodeWithBitmapFactory(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PhotoConverter.MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return upright(bitmap, bytes)
    }

    /** BitmapFactory ignores the camera's "which way is up" tag (ImageDecoder applies it): turn the photo upright. */
    private fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatchingExceptions {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        Timber.d("Notes: turned a photo upright (orientation %d)", orientation)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= PhotoConverter.MAX_SIDE) return bitmap
        val ratio = PhotoConverter.MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}
