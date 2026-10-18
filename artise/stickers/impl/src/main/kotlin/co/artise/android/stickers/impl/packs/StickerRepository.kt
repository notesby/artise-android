/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.packs

import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A made sticker, ready to upload: its picture and size in pixels. */
@Suppress("UseDataClass")
class StickerPicture(val bytes: ByteArray, val width: Int, val height: Int, val mimeType: String)

/** The person's own stickers (kept in their account, on all their devices) and the starter pack. */
interface StickerRepository {
    val myStickers: StateFlow<List<Sticker>>

    suspend fun refresh(): Result<Unit>

    fun starterStickers(language: String): List<Sticker>

    suspend fun addToMine(picture: StickerPicture, description: String): Result<Sticker>

    /** Adds a sticker whose picture is on the server already (one someone sent). */
    suspend fun addUploaded(sticker: Sticker): Result<Sticker>

    suspend fun remove(sticker: Sticker): Result<Unit>

    /** The sticker's picture on the server; a starter sticker is uploaded the first time, then remembered. */
    suspend fun mxcUrlFor(sticker: Sticker): Result<String>
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultStickerRepository(
    private val matrixClient: MatrixClient,
    private val starterPack: StarterPack,
    private val dispatchers: CoroutineDispatchers,
) : StickerRepository {
    private val mine = MutableStateFlow<List<Sticker>>(emptyList())
    override val myStickers: StateFlow<List<Sticker>> = mine.asStateFlow()

    // Reading and writing the pack happen one at a time, so two stickers saved together both stay.
    private val packLock = Mutex()

    override suspend fun refresh(): Result<Unit> = matrixClient.getAccountData(ImagePack.USER_PACK_EVENT_TYPE).map { content ->
        mine.value = ImagePack.stickers(content)
    }

    override fun starterStickers(language: String): List<Sticker> = starterPack.stickers(language)

    override suspend fun addToMine(picture: StickerPicture, description: String): Result<Sticker> = runCatchingExceptions {
        val url = matrixClient.uploadMedia(picture.mimeType, picture.bytes).getOrThrow()
        val sticker = Sticker(
            id = "",
            description = description,
            source = StickerSource.Uploaded(url),
            width = picture.width,
            height = picture.height,
            mimeType = picture.mimeType,
            size = picture.bytes.size.toLong(),
        )
        addUploaded(sticker).getOrThrow()
    }

    override suspend fun addUploaded(sticker: Sticker): Result<Sticker> = runCatchingExceptions {
        packLock.withLock {
            val content = matrixClient.getAccountData(ImagePack.USER_PACK_EVENT_TYPE).getOrThrow()
            val shortcode = ImagePack.freeShortcode(content)
            val updated = ImagePack.withSticker(content, shortcode, sticker, PACK_NAME)
            matrixClient.setAccountData(ImagePack.USER_PACK_EVENT_TYPE, updated).getOrThrow()
            mine.value = ImagePack.stickers(updated)
            sticker.copy(id = shortcode)
        }
    }

    override suspend fun remove(sticker: Sticker): Result<Unit> = runCatchingExceptions {
        packLock.withLock {
            val content = matrixClient.getAccountData(ImagePack.USER_PACK_EVENT_TYPE).getOrThrow()
            val updated = ImagePack.withoutSticker(content, sticker.id)
            matrixClient.setAccountData(ImagePack.USER_PACK_EVENT_TYPE, updated).getOrThrow()
            mine.value = ImagePack.stickers(updated)
        }
    }

    override suspend fun mxcUrlFor(sticker: Sticker): Result<String> = when (val source = sticker.source) {
        is StickerSource.Uploaded -> Result.success(source.mxcUrl)
        is StickerSource.Starter -> runCatchingExceptions {
            packLock.withLock {
                val uploads = matrixClient.getAccountData(UPLOADS_EVENT_TYPE).getOrThrow().toJsonObject()
                (uploads[sticker.id] as? JsonPrimitive)?.content ?: run {
                    val bytes = withContext(dispatchers.io) { starterPack.bytes(source.assetPath) }
                    val url = matrixClient.uploadMedia(sticker.mimeType, bytes).getOrThrow()
                    val updated = JsonObject(uploads + (sticker.id to JsonPrimitive(url)))
                    matrixClient.setAccountData(UPLOADS_EVENT_TYPE, updated.toString()).getOrThrow()
                    url
                }
            }
        }
    }

    private fun String?.toJsonObject(): Map<String, kotlinx.serialization.json.JsonElement> =
        this?.let { runCatchingExceptions { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: emptyMap()

    private companion object {
        const val PACK_NAME = "My stickers"

        /** Where starter stickers already uploaded by this account are remembered, so they upload once. */
        const val UPLOADS_EVENT_TYPE = "co.artise.sticker_uploads"
    }
}
