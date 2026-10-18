/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.packs

import co.artise.android.stickers.impl.Sticker
import co.artise.android.stickers.impl.StickerSource
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A person's own sticker pack, kept in their account as an "image pack" (MSC2545, `im.ponies.user_emotes`), the
 * format FluffyChat, Cinny and Nheko read too. Everything Artise doesn't use, like custom emojis added by another app,
 * is kept as it is.
 */
internal object ImagePack {
    const val USER_PACK_EVENT_TYPE = "im.ponies.user_emotes"

    private val json = Json { ignoreUnknownKeys = true }

    /** The stickers in [content]; images marked only as emojis aren't stickers. */
    fun stickers(content: String?): List<Sticker> {
        val images = parse(content)["images"] as? JsonObject ?: return emptyList()
        return images.mapNotNull { (shortcode, value) ->
            val image = value as? JsonObject ?: return@mapNotNull null
            val url = (image["url"] as? JsonPrimitive)?.contentOrNull() ?: return@mapNotNull null
            val usage = (image["usage"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
            if (usage != null && "sticker" !in usage) return@mapNotNull null
            val info = image["info"] as? JsonObject
            Sticker(
                id = shortcode,
                description = (image["body"] as? JsonPrimitive)?.contentOrNull() ?: shortcode,
                source = StickerSource.Uploaded(url),
                width = info?.get("w")?.jsonPrimitive?.intOrNull ?: DEFAULT_SIDE,
                height = info?.get("h")?.jsonPrimitive?.intOrNull ?: DEFAULT_SIDE,
                mimeType = (info?.get("mimetype") as? JsonPrimitive)?.contentOrNull() ?: "image/webp",
                size = info?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }

    /** [content] with [sticker] added under [shortcode]; the pack gets a name if it has none. */
    fun withSticker(content: String?, shortcode: String, sticker: Sticker, packName: String): String {
        val url = (sticker.source as? StickerSource.Uploaded)?.mxcUrl ?: error("Only uploaded stickers go in a pack")
        val root = parse(content)
        val images = (root["images"] as? JsonObject).orEmpty() + (
            shortcode to buildJsonObject {
                put("url", url)
                put("body", sticker.description)
                putJsonObject("info") {
                    put("w", sticker.width)
                    put("h", sticker.height)
                    put("mimetype", sticker.mimeType)
                    put("size", sticker.size)
                }
                putJsonArray("usage") { add(JsonPrimitive("sticker")) }
            }
            )
        val pack = root["pack"] as? JsonObject ?: buildJsonObject {
            put("display_name", packName)
            putJsonArray("usage") { add(JsonPrimitive("sticker")) }
        }
        return JsonObject(root + mapOf("images" to JsonObject(images), "pack" to pack)).toString()
    }

    /** [content] without the image [shortcode]. */
    fun withoutSticker(content: String?, shortcode: String): String {
        val root = parse(content)
        val images = (root["images"] as? JsonObject).orEmpty() - shortcode
        return JsonObject(root + ("images" to JsonObject(images))).toString()
    }

    /** A short name that isn't taken yet: "sticker_1", "sticker_2"... */
    fun freeShortcode(content: String?): String {
        val taken = (parse(content)["images"] as? JsonObject)?.keys.orEmpty()
        return generateSequence(1) { it + 1 }.map { "sticker_$it" }.first { it !in taken }
    }

    private fun parse(content: String?): JsonObject = content
        ?.let { runCatchingExceptions { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: JsonObject(emptyMap())

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

    private const val DEFAULT_SIDE = 256
}
