/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.send

import co.artise.android.stickers.impl.Sticker
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A Matrix sticker message (`m.sticker`), which Element, FluffyChat and the others show as a sticker. */
internal object StickerMessage {
    const val EVENT_TYPE = "m.sticker"

    fun content(sticker: Sticker, mxcUrl: String): String = buildJsonObject {
        put("body", sticker.description)
        put("url", mxcUrl)
        putJsonObject("info") {
            put("w", sticker.width)
            put("h", sticker.height)
            put("mimetype", sticker.mimeType)
            if (sticker.size > 0) put("size", sticker.size)
        }
    }.toString()
}
