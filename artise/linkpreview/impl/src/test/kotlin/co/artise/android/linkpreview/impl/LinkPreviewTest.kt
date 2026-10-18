/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import co.artise.android.linkpreview.api.DraftLinkPreview
import co.artise.android.linkpreview.api.DraftLinkPreviewImage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.io.File

class LinkPreviewTest {
    /** The first web link is found, without the punctuation around it; chat links (matrix.to) are skipped. */
    @Test
    fun `finds the link to preview`() {
        assertThat(LinkFinder.firstLink("mira https://www.youtube.com/watch?v=abc123.")).isEqualTo("https://www.youtube.com/watch?v=abc123")
        assertThat(LinkFinder.firstLink("(ver https://es.wikipedia.org/wiki/Mole_(salsa))")).isEqualTo("https://es.wikipedia.org/wiki/Mole_(salsa)")
        assertThat(LinkFinder.firstLink("en www.artise.co, ¿la viste?")).isEqualTo("https://www.artise.co")
        assertThat(LinkFinder.firstLink("https://matrix.to/#/@ana:artise.co y http://example.com/a")).isEqualTo("http://example.com/a")
        assertThat(LinkFinder.firstLink("nada aquí, ni https:// ni www")).isNull()
        assertThat(LinkFinder.firstLink("https://localhost/x")).isNull()
    }

    /** Open Graph tags first, then Twitter's, then the page's own title and description; relative pictures resolved. */
    @Test
    fun `reads what a page says about itself`() {
        val page = """
            <html><head>
              <title>Ignored title</title>
              <meta property="og:title" content="  Mole   poblano &amp; arroz ">
              <meta name="twitter:description" content="La receta de la abuela">
              <meta property="og:site_name" content="Cocina">
              <meta property="og:image" content="/img/mole.jpg">
            </head><body>…</body></html>
        """.trimIndent()
        assertThat(PageMetadata.parse(page, "https://cocina.example/recetas/mole")).isEqualTo(
            PageMetadata("Mole poblano & arroz", "La receta de la abuela", "Cocina", "https://cocina.example/img/mole.jpg")
        )
        val plain = PageMetadata.parse("<title>Example Domain</title><meta name=description content=Hola>", "https://example.com")
        assertThat(plain).isEqualTo(PageMetadata("Example Domain", "Hola", null, null))
        assertThat(PageMetadata.parse("<p>nada</p>", "https://example.com").isEmpty).isTrue()
        assertThat(PageMetadata.parse("<title>${"a".repeat(500)}</title>", "https://example.com").title).hasLength(200)
    }

    /** What's encrypted decrypts back, with the hash checked; every file gets its own key. */
    @Test
    fun `encrypts pictures for encrypted chats`() {
        val plain = ByteArray(5000) { it.toByte() }
        val first = AttachmentEncryption.encrypt(plain)
        assertThat(first.bytes).isNotEqualTo(plain)
        assertThat(AttachmentEncryption.decrypt(first)).isEqualTo(plain)
        assertThat(AttachmentEncryption.encrypt(plain).key).isNotEqualTo(first.key)
        // The counter half of the IV starts at zero, as Matrix asks.
        assertThat(java.util.Base64.getDecoder().decode(first.iv).takeLast(8).all { it == 0.toByte() }).isTrue()
    }

    /** In a plain chat the picture is an mxc URL; in an encrypted one it's an encrypted file the reader turns into a source. */
    @Test
    fun `previews go in the message and come back out`() {
        val draft = DraftLinkPreview(
            url = "https://cocina.example/mole",
            title = "Mole",
            description = "La receta",
            siteName = "Cocina",
            image = DraftLinkPreviewImage(File("x.jpg"), 800, 420, 1234),
        )
        val plain = LinkPreviewContent.build(draft, UploadedImage("mxc://artise.co/plain", 800, 420, 1234, null))
        val entry = Json.parseToJsonElement(plain).jsonObject["com.beeper.linkpreviews"]!!.jsonArray[0].jsonObject
        assertThat(entry["og:image"].toString()).isEqualTo("\"mxc://artise.co/plain\"")
        val read = LinkPreviewContent.read("""{"type": "m.room.message", "content": {"body": "x", "com.beeper.linkpreviews": ${entry.let { "[$it]" }}}}""")!!
        assertThat(read.title).isEqualTo("Mole")
        assertThat(read.image?.source?.json).isNull()
        assertThat(read.image?.width).isEqualTo(800)

        val encrypted = LinkPreviewContent.build(draft, UploadedImage("mxc://artise.co/secret", 800, 420, 1234, AttachmentEncryption.encrypt(byteArrayOf(1))))
        val readEncrypted = LinkPreviewContent.read(encrypted)!!
        val file = Json.parseToJsonElement(readEncrypted.image!!.source.json!!).jsonObject["file"]!!.jsonObject
        assertThat(file["url"].toString()).isEqualTo("\"mxc://artise.co/secret\"")
        assertThat(file["key"]!!.jsonObject["alg"].toString()).isEqualTo("\"A256CTR\"")
        assertThat(file.keys).containsAtLeast("iv", "hashes", "v")
        assertThat(encrypted).doesNotContain("og:image\"")

        // Without a picture, and with nothing readable, there's no preview.
        assertThat(LinkPreviewContent.read(LinkPreviewContent.build(draft.copy(image = null), null))?.image).isNull()
        assertThat(LinkPreviewContent.read("""{"content": {"com.beeper.linkpreviews": [{"matched_url": "https://a.b"}]}}""")).isNull()
        assertThat(LinkPreviewContent.read("not json")).isNull()
    }
}
