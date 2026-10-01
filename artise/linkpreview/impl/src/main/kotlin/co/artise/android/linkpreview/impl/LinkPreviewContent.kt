/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import co.artise.android.linkpreview.api.DraftLinkPreview
import co.artise.android.linkpreview.api.LinkPreview
import co.artise.android.linkpreview.api.LinkPreviewImage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The preview's picture once uploaded: plain, or [encryption] for an encrypted chat. */
internal data class UploadedImage(val mxcUrl: String, val width: Int, val height: Int, val size: Long, val encryption: EncryptedAttachment?)

/**
 * Link previews inside messages, as MSC4095 describes them (with Beeper's `com.beeper.linkpreviews` field, which
 * other apps already read): Open Graph keys for the page, and the picture as `og:image` or, in encrypted chats,
 * `beeper:image:encryption`.
 */
internal object LinkPreviewContent {
    const val FIELD = "com.beeper.linkpreviews"

    // MSC4095's name once it's in the spec; read too, so previews keep working when apps switch.
    private const val STABLE_FIELD = "m.url_previews"

    /** The JSON object added to the message's content. */
    fun build(preview: DraftLinkPreview, image: UploadedImage?): String = buildJsonObject {
        putJsonArray(FIELD) {
            add(
                buildJsonObject {
                    put("matched_url", preview.url)
                    put("og:url", preview.url)
                    preview.title?.let { put("og:title", it) }
                    preview.description?.let { put("og:description", it) }
                    preview.siteName?.let { put("og:site_name", it) }
                    if (image != null) {
                        put("og:image:type", "image/jpeg")
                        put("og:image:width", image.width)
                        put("og:image:height", image.height)
                        put("matrix:image:size", image.size)
                        val encryption = image.encryption
                        if (encryption == null) {
                            put("og:image", image.mxcUrl)
                        } else {
                            put("beeper:image:encryption", encryptedFile(image.mxcUrl, encryption))
                        }
                    }
                }
            )
        }
    }.toString()

    /** The first preview in an event (the whole event, or just its content) that has a link, or `null`. */
    fun read(eventJson: String): LinkPreview? {
        val root = runCatchingExceptions { Json.parseToJsonElement(eventJson).jsonObject }.getOrNull() ?: return null
        val content = root["content"] as? JsonObject ?: root
        val previews = (content[FIELD] ?: content[STABLE_FIELD]) as? JsonArray ?: return null
        return previews.firstNotNullOfOrNull { (it as? JsonObject)?.toLinkPreview() }
    }

    private fun JsonObject.toLinkPreview(): LinkPreview? {
        val url = (string("matched_url") ?: string("og:url"))?.takeIf { it.startsWith("http") } ?: return null
        val title = string("og:title")
        val description = string("og:description")
        if (title == null && description == null) return null
        val encryption = this["beeper:image:encryption"] as? JsonObject
        val source = when {
            encryption != null -> encryption.string("url")?.let { MediaSource(it, json = buildJsonObject { put("file", encryption) }.toString()) }
            else -> string("og:image")?.takeIf { it.startsWith("mxc://") }?.let { MediaSource(it) }
        }
        return LinkPreview(
            url = url,
            title = title,
            description = description,
            siteName = string("og:site_name"),
            image = source?.let { LinkPreviewImage(it, width = int("og:image:width"), height = int("og:image:height")) },
        )
    }

    private fun encryptedFile(mxcUrl: String, encryption: EncryptedAttachment) = buildJsonObject {
        put("url", mxcUrl)
        putJsonObject("key") {
            put("kty", "oct")
            putJsonArray("key_ops") {
                add(JsonPrimitive("encrypt"))
                add(JsonPrimitive("decrypt"))
            }
            put("alg", "A256CTR")
            put("k", encryption.key)
            put("ext", true)
        }
        put("iv", encryption.iv)
        putJsonObject("hashes") { put("sha256", encryption.sha256) }
        put("v", "v2")
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.ifBlank { null }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
