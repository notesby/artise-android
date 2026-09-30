/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LivePreviewTest {
    private val styles = LivePreviewStyles(
        heading1 = SpanStyle(color = Color(1)),
        heading2 = SpanStyle(color = Color(2)),
        heading3 = SpanStyle(color = Color(3)),
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        italic = SpanStyle(fontStyle = FontStyle.Italic),
        strikethrough = SpanStyle(color = Color(4)),
        code = SpanStyle(color = Color(5)),
        link = SpanStyle(color = Color(6)),
        dim = SpanStyle(color = Color(7)),
        checkbox = SpanStyle(color = Color(8)),
    )

    private val note = "# Súper\n- [ ] leche\n- [x] **pan** integral\nVer [[Recetas/Mole|el mole]] y [web](https://artise.co)"

    private fun shown(text: String, raw: IntRange) = LivePreview.transform(text, raw, styles).text.text

    /** Lines away from the cursor show formatted with their marks hidden; the cursor's line shows as written. */
    @Test
    fun `other lines are formatted and the cursor line is raw`() {
        assertThat(shown(note, 1..1)).isEqualTo("Súper\n- [ ] leche\n☑ pan integral\nVer el mole y web")
        assertThat(shown(note, 2..2)).isEqualTo("Súper\n☐ leche\n- [x] **pan** integral\nVer el mole y web")
    }

    /** Styles land on the visible words: the heading, the bold word, the links. */
    @Test
    fun `styles cover the right words`() {
        val text = LivePreview.transform(note, 1..1, styles).text
        fun styleOf(word: String) = text.spanStyles.filter {
            it.start <= text.text.indexOf(
            word
        ) && it.end >= text.text.indexOf(word) + word.length
        }.map { it.item }
        assertThat(styleOf("Súper")).contains(styles.heading1)
        assertThat(styleOf("pan")).contains(styles.bold)
        assertThat(styleOf("el mole")).contains(styles.link)
        assertThat(styleOf("web")).contains(styles.link)
    }

    /** Every position maps back and forth, so typing and the cursor land where the person sees them. */
    @Test
    fun `offsets map both ways`() {
        val mapping = LivePreview.transform(note, 1..1, styles).offsetMapping
        val shownText = shown(note, 1..1)
        // "pan" in the formatted line is the "pan" between the ** in the Markdown.
        assertThat(mapping.transformedToOriginal(shownText.indexOf("pan"))).isEqualTo(note.indexOf("pan"))
        assertThat(mapping.originalToTransformed(note.indexOf("integral"))).isEqualTo(shownText.indexOf("integral"))
        for (i in 0..note.length) {
            val out = mapping.originalToTransformed(i)
            assertThat(out).isIn(0..shownText.length)
            assertThat(mapping.transformedToOriginal(out)).isIn(0..note.length)
        }
        // Positions never go backwards, or the cursor would jump.
        val back = (0..shownText.length).map { mapping.transformedToOriginal(it) }
        assertThat(back).isInOrder()
    }

    /** A tap on a checkbox is a checkbox hit; on the text next to it, it isn't. */
    @Test
    fun `checkbox taps are recognised`() {
        val mapping = LivePreview.transform(note, 0..0, styles).offsetMapping
        val shownText = shown(note, 0..0)
        val box = mapping.transformedToOriginal(shownText.indexOf('☐'))
        assertThat(LivePreview.hitAt(note, box)).isEqualTo(LivePreviewHit.Checkbox(1))
        val word = mapping.transformedToOriginal(shownText.indexOf("leche") + 2)
        assertThat(LivePreview.hitAt(note, word)).isNull()
    }

    /** A tap on a link's text is a link hit, with its target and shown text ready for the dialog. */
    @Test
    fun `link taps are recognised`() {
        val mapping = LivePreview.transform(note, 0..0, styles).offsetMapping
        val shownText = shown(note, 0..0)
        val hit = LivePreview.hitAt(note, mapping.transformedToOriginal(shownText.indexOf("mole")))
        assertThat(hit).isInstanceOf(LivePreviewHit.Link::class.java)
        val link = (hit as LivePreviewHit.Link).link
        assertThat(link).isEqualTo(EditableLink(isNote = true, target = "Recetas/Mole", shownText = "el mole"))
        assertThat(note.substring(hit.start, hit.end)).isEqualTo("[[Recetas/Mole|el mole]]")
        val web = LivePreview.hitAt(note, mapping.transformedToOriginal(shownText.indexOf("web") + 1)) as LivePreviewHit.Link
        assertThat(web.link).isEqualTo(EditableLink(isNote = false, target = "https://artise.co", shownText = "web"))
    }

    /** Code blocks hide their fences until the cursor is inside; then the whole block shows raw. */
    @Test
    fun `code blocks`() {
        val text = "Antes\n```\nx = 1\n```\nDespués"
        assertThat(shown(text, 0..0)).isEqualTo("Antes\n\nx = 1\n\nDespués")
        assertThat(LivePreview.rawLines(text, text.indexOf("x"), text.indexOf("x"))).isEqualTo(1..3)
    }

    /** Tables show as rows with dimmed pipes; with the cursor in one row, the whole table shows raw. */
    @Test
    fun tables() {
        val text = "| Paso | Tiempo |\n| --- | --- |\n| Freír | 10 |\nFin"
        assertThat(shown(text, 3..3)).isEqualTo("| Paso | Tiempo |\n| --- | --- |\n| Freír | 10 |\nFin")
        assertThat(LivePreview.rawLines(text, text.indexOf("Freír"), text.indexOf("Freír"))).isEqualTo(0..2)
        val spans = LivePreview.transform(text, 3..3, styles).text.spanStyles
        val paso = text.indexOf("Paso")
        assertThat(spans.filter { it.start <= paso && it.end > paso }.map { it.item.fontWeight }).contains(FontWeight.Bold)
    }

    /** Lists, quotes and headings show clean markers; italics and inline code lose their marks. */
    @Test
    fun `other formatting`() {
        val text = "## Plan\n- uno\n> cita\n*nota* y `code`\n1. primero"
        assertThat(shown(text, 4..4)).isEqualTo("Plan\n• uno\n▍ cita\nnota y code\n1. primero")
    }

    /** Web addresses typed as plain text show as links, and tapping one edits it; its text isn't changed. */
    @Test
    fun `bare web addresses`() {
        val text = "Compras en https://tienda.mx/ofertas. Y www.artise.co\nfin"
        val transformed = LivePreview.transform(text, 1..1, styles)
        assertThat(transformed.text.text).isEqualTo(text)
        val url = "https://tienda.mx/ofertas"
        assertThat(transformed.text.spanStyles.any { it.item == styles.link && transformed.text.text.substring(it.start, it.end) == url }).isTrue()
        val hit = LivePreview.hitAt(text, text.indexOf("tienda")) as LivePreviewHit.Link
        assertThat(hit.link).isEqualTo(EditableLink(isNote = false, target = url, shownText = ""))
        assertThat((LivePreview.hitAt(text, text.indexOf("artise")) as LivePreviewHit.Link).link.target).isEqualTo("www.artise.co")
    }

    /** An address inside [text](address) is part of that link, not a second one. */
    @Test
    fun `addresses in markdown links are not separate links`() {
        val text = "[web](https://artise.co)"
        assertThat(LivePreview.transform(text, 1..1, styles).text.text).isEqualTo("web")
    }

    /** The dialog writes links back as Markdown, and removing a link keeps the text people saw. */
    @Test
    fun `links write back`() {
        assertThat(EditableLink(true, "Recetas/Mole", "el mole").toMarkdown()).isEqualTo("[[Recetas/Mole|el mole]]")
        assertThat(EditableLink(true, "Súper", "").toMarkdown()).isEqualTo("[[Súper]]")
        assertThat(EditableLink(false, "https://artise.co", "").toMarkdown()).isEqualTo("https://artise.co")
        assertThat(EditableLink(false, "https://artise.co", "Artise").toMarkdown()).isEqualTo("[Artise](https://artise.co)")
        assertThat(EditableLink(true, "Recetas/Mole#Salsa", "").label()).isEqualTo("Mole")
    }
}
