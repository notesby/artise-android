/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.artise.android.linkpreview.api.DraftLinkPreview
import co.artise.android.linkpreview.api.DraftLinkPreviewImage
import co.artise.android.linkpreview.api.LinkPreviewService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.interceptors.DynamicHttpLoggingInterceptor
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

// Enough for any page's <head>; the rest isn't needed.
private const val MAX_PAGE_BYTES = 1024 * 1024
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val MAX_IMAGE_SIDE = 800
private const val MIN_IMAGE_SIDE = 48
private const val JPEG_QUALITY = 80
private const val TIMEOUT_SECONDS = 8L

// Pictures kept for the composer only until the message is sent, or a day at most.
private const val KEEP_DRAFT_IMAGES_MS = 24 * 60 * 60 * 1000L

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultLinkPreviewService(
    okHttpClient: OkHttpClient,
    @ApplicationContext context: Context,
    private val matrixClient: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
) : LinkPreviewService {
    // The app's client logs whole answers when debug logging is on: not wanted for the pages people share.
    private val client = okHttpClient.newBuilder()
        .apply { interceptors().removeAll { it is DynamicHttpLoggingInterceptor } }
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val imagesDir = File(context.cacheDir, "link_previews")

    override fun firstLink(text: String): String? = LinkFinder.firstLink(text)

    override suspend fun fetch(url: String): Result<DraftLinkPreview> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val (metadata, pageUrl) = get(url, accept = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5").use { response ->
                val type = response.body.contentType()
                require(type == null || type.subtype.contains("html")) { "Not a web page: $type" }
                val bytes = response.body.byteStream().readAtMost(MAX_PAGE_BYTES)
                PageMetadata.parse(bytes, type?.charset()?.name(), response.request.url.toString()) to response.request.url.toString()
            }
            require(!metadata.isEmpty) { "Nothing to preview" }
            DraftLinkPreview(
                url = url,
                title = metadata.title,
                description = metadata.description,
                siteName = metadata.siteName,
                image = metadata.imageUrl?.let { imageUrl ->
                    runCatchingExceptions { downloadImage(imageUrl, referer = pageUrl) }
                        .onFailure { Timber.w("Link preview picture not loaded: ${it.message}") }
                        .getOrNull()
                },
            )
        }
    }

    override suspend fun extraContent(preview: DraftLinkPreview, encrypted: Boolean): String {
        val image = preview.image?.let { image ->
            runCatchingExceptions {
                val bytes = withContext(dispatchers.io) { image.file.readBytes() }
                val encryption = if (encrypted) AttachmentEncryption.encrypt(bytes) else null
                val mxcUrl = if (encryption != null) {
                    matrixClient.uploadMedia("application/octet-stream", encryption.bytes)
                } else {
                    matrixClient.uploadMedia("image/jpeg", bytes)
                }.getOrThrow()
                UploadedImage(mxcUrl, image.width, image.height, bytes.size.toLong(), encryption)
            }
                .onFailure { Timber.w(it, "Link preview picture not uploaded; sending the preview without it") }
                .getOrNull()
        }
        return LinkPreviewContent.build(preview, image)
    }

    private fun get(url: String, accept: String, referer: String? = null): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.5")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            error("HTTP ${response.code}")
        }
        return response
    }

    /** The page's picture, at most [MAX_IMAGE_SIDE] px, as a JPEG in the cache. */
    private fun downloadImage(url: String, referer: String): DraftLinkPreviewImage {
        val bytes = get(url, accept = "image/*", referer = referer).use { it.body.byteStream().readAtMost(MAX_IMAGE_BYTES) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth >= MIN_IMAGE_SIDE && bounds.outHeight >= MIN_IMAGE_SIDE) { "Picture too small or unreadable" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Picture unreadable")
        val scale = MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else {
            decoded
        }
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        imagesDir.mkdirs()
        imagesDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > KEEP_DRAFT_IMAGES_MS }?.forEach { it.delete() }
        val file = File(imagesDir, UUID.randomUUID().toString() + ".jpg").apply { writeBytes(jpeg) }
        return DraftLinkPreviewImage(file, bitmap.width, bitmap.height, jpeg.size.toLong())
    }

    private companion object {
        // A browser's, so pages answer as they would to the person; "Artise" says who's asking.
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 Artise"
    }
}

/** Up to [limit] bytes, then stops reading. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
