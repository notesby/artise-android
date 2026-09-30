/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl

import co.artise.android.stickers.impl.maker.PixelBox
import co.artise.android.stickers.impl.maker.StickerGeometry
import co.artise.android.stickers.impl.packs.ImagePack
import co.artise.android.stickers.impl.packs.StarterPackParser
import co.artise.android.stickers.impl.send.KeyboardStickers
import co.artise.android.stickers.impl.send.StickerMessage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class StickerFormatsTest {
    private val uploaded = Sticker("", "Sticker", StickerSource.Uploaded("mxc://artise.co/abc"), 300, 200, "image/webp", 1234)

    /** A new pack gets the sticker and a name; the image is marked as a sticker for other apps. */
    @Test
    fun `adding to an empty pack`() {
        val content = ImagePack.withSticker(null, "sticker_1", uploaded, "My stickers")
        val stickers = ImagePack.stickers(content)
        assertThat(stickers).containsExactly(uploaded.copy(id = "sticker_1"))
        val root = Json.parseToJsonElement(content).jsonObject
        assertThat(root["pack"]!!.jsonObject["display_name"]!!.jsonPrimitive.content).isEqualTo("My stickers")
    }

    /** Custom emojis another app keeps in the same pack are left alone, and aren't shown as stickers. */
    @Test
    fun `other apps' emojis are kept and not shown`() {
        val fromFluffyChat = """{"images": {"blobcat": {"url": "mxc://x/cat", "usage": ["emoticon"]}}, "pack": {"display_name": "Mine"}}"""
        val content = ImagePack.withSticker(fromFluffyChat, "sticker_1", uploaded, "My stickers")
        assertThat(ImagePack.stickers(content).map { it.id }).containsExactly("sticker_1")
        val root = Json.parseToJsonElement(content).jsonObject
        assertThat(root["images"]!!.jsonObject.keys).containsExactly("blobcat", "sticker_1")
        assertThat(root["pack"]!!.jsonObject["display_name"]!!.jsonPrimitive.content).isEqualTo("Mine")
        // An image without "usage" is both an emoji and a sticker.
        assertThat(ImagePack.stickers("""{"images": {"both": {"url": "mxc://x/y"}}}""").map { it.id }).containsExactly("both")
    }

    /** Removing takes out just that sticker; new names skip the ones taken. */
    @Test
    fun `removing and naming`() {
        val two = ImagePack.withSticker(ImagePack.withSticker(null, "sticker_1", uploaded, "P"), "sticker_2", uploaded, "P")
        assertThat(ImagePack.freeShortcode(two)).isEqualTo("sticker_3")
        val one = ImagePack.withoutSticker(two, "sticker_1")
        assertThat(ImagePack.stickers(one).map { it.id }).containsExactly("sticker_2")
        assertThat(ImagePack.freeShortcode(one)).isEqualTo("sticker_1")
        assertThat(ImagePack.stickers("not json")).isEmpty()
    }

    /** The message other apps read: text, picture, and its size so they can lay it out before it loads. */
    @Test
    fun `sticker message`() {
        val content = Json.parseToJsonElement(StickerMessage.content(uploaded, "mxc://artise.co/abc")).jsonObject
        assertThat(content["body"]!!.jsonPrimitive.content).isEqualTo("Sticker")
        assertThat(content["url"]!!.jsonPrimitive.content).isEqualTo("mxc://artise.co/abc")
        val info = content["info"]!!.jsonObject
        assertThat(info["w"]!!.jsonPrimitive.content).isEqualTo("300")
        assertThat(info["mimetype"]!!.jsonPrimitive.content).isEqualTo("image/webp")
    }

    /** The starter pack speaks the app's language: Spanish names in Spanish, English otherwise. */
    @Test
    fun `starter pack names`() {
        val manifest = """{"stickers": [{"id": "coffee", "en": "Hot beverage", "es": "Café", "w": 256, "h": 256, "size": 5000}]}"""
        assertThat(StarterPackParser.stickers(manifest, "es").single().description).isEqualTo("Café")
        val sticker = StarterPackParser.stickers(manifest, "fr").single()
        assertThat(sticker.description).isEqualTo("Hot beverage")
        assertThat(sticker.id).isEqualTo("starter:coffee")
        assertThat(sticker.source).isEqualTo(StickerSource.Starter("stickers/starter/coffee.webp"))
    }

    /** A cut-out is trimmed to what's visible, with a little air; nothing visible means nothing was cut out. */
    @Test
    fun `trimming a cut-out`() {
        val box = StickerGeometry.visibleBox(10, 10, padding = 1) { x, y -> if (x in 3..5 && y in 4..6) 255 else 0 }
        assertThat(box).isEqualTo(PixelBox(2, 3, 7, 8))
        assertThat(StickerGeometry.visibleBox(4, 4, padding = 1) { _, _ -> 5 }).isNull()
        assertThat(StickerGeometry.centerSquare(300, 200)).isEqualTo(PixelBox(50, 0, 250, 200))
    }

    /** Stickers are at most 512 px, keep their shape, and small ones aren't blown up. */
    @Test
    fun `sticker size`() {
        assertThat(StickerGeometry.fit(1024, 768)).isEqualTo(512 to 384)
        assertThat(StickerGeometry.fit(200, 100)).isEqualTo(200 to 100)
    }

    /** From a keyboard, small WebP, PNG and GIF pictures are stickers; photos and big files aren't. */
    @Test
    fun `keyboard stickers`() {
        assertThat(KeyboardStickers.isStickerType("image/webp")).isTrue()
        assertThat(KeyboardStickers.isStickerType("image/GIF")).isTrue()
        assertThat(KeyboardStickers.isStickerType("image/jpeg")).isFalse()
        assertThat(KeyboardStickers.isStickerType(null)).isFalse()
        assertThat(KeyboardStickers.isStickerSize(500_000)).isTrue()
        assertThat(KeyboardStickers.isStickerSize(3_000_000)).isFalse()
    }
}
