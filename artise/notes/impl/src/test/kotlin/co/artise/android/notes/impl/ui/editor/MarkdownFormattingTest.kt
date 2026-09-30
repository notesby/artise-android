/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownFormattingTest {
    private fun apply(action: FormatAction, text: String, start: Int, end: Int = start) = MarkdownFormatting.apply(action, text, start, end)

    /** Bold wraps the selection and keeps it selected; pressing again removes it. */
    @Test
    fun `bold toggles around a selection`() {
        val bold = apply(FormatAction.BOLD, "comprar pan hoy", 8, 11)
        assertThat(bold).isEqualTo(EditedText("comprar **pan** hoy", 10, 13))
        assertThat(apply(FormatAction.BOLD, bold.text, bold.start, bold.end)).isEqualTo(EditedText("comprar pan hoy", 8, 11))
    }

    /** With no selection, an empty pair is inserted and the cursor goes between, ready to type. */
    @Test
    fun `wrap with a cursor inserts a pair`() {
        assertThat(apply(FormatAction.ITALIC, "hola ", 5)).isEqualTo(EditedText("hola **", 6, 6))
        assertThat(apply(FormatAction.STRIKETHROUGH, "", 0)).isEqualTo(EditedText("~~~~", 2, 2))
    }

    /** Italic inside bold text adds its own marker instead of removing half of the bold one. */
    @Test
    fun `italic does not eat bold`() {
        assertThat(apply(FormatAction.ITALIC, "**pan**", 2, 5).text).isEqualTo("***pan***")
    }

    /** The heading button cycles # → ## → ### → none on the cursor's line. */
    @Test
    fun `heading cycles`() {
        val h1 = apply(FormatAction.HEADING, "intro\nMole", 8)
        assertThat(h1).isEqualTo(EditedText("intro\n# Mole", 10, 10))
        val h2 = apply(FormatAction.HEADING, h1.text, h1.start)
        assertThat(h2.text).isEqualTo("intro\n## Mole")
        val h3 = apply(FormatAction.HEADING, h2.text, h2.start)
        assertThat(h3.text).isEqualTo("intro\n### Mole")
        assertThat(apply(FormatAction.HEADING, h3.text, h3.start).text).isEqualTo("intro\nMole")
    }

    /** A list applies to every selected line, numbered in order, and blank lines stay blank. */
    @Test
    fun `lists apply to selected lines`() {
        val text = "leche\npan\n\nhuevos"
        assertThat(apply(FormatAction.NUMBERED_LIST, text, 0, text.length).text).isEqualTo("1. leche\n2. pan\n\n3. huevos")
        assertThat(apply(FormatAction.CHECKLIST, "leche\npan", 0, 9).text).isEqualTo("- [ ] leche\n- [ ] pan")
    }

    /** Pressing the same list button again removes it; another list kind replaces it. */
    @Test
    fun `lists toggle and switch`() {
        assertThat(apply(FormatAction.BULLET_LIST, "- leche\n- pan", 0, 13).text).isEqualTo("leche\npan")
        assertThat(apply(FormatAction.CHECKLIST, "- leche", 3).text).isEqualTo("- [ ] leche")
        assertThat(apply(FormatAction.QUOTE, "1. leche", 4).text).isEqualTo("> leche")
    }

    /** With a cursor, a list button changes only its line and keeps the cursor on the same word. */
    @Test
    fun `list with a cursor keeps its place`() {
        assertThat(apply(FormatAction.BULLET_LIST, "uno\ndos", 5)).isEqualTo(EditedText("uno\n- dos", 7, 7))
    }

    /** The link button types "[[" so the note suggestions appear. */
    @Test
    fun `note link opens suggestions`() {
        assertThat(apply(FormatAction.NOTE_LINK, "Ver ", 4)).isEqualTo(EditedText("Ver [[", 6, 6))
    }

    /** Enter continues a list: same bullet, next number, a new unticked checkbox, keeping indentation. */
    @Test
    fun `enter continues lists`() {
        assertThat(MarkdownFormatting.continueListOnEnter("- leche", 7)).isEqualTo(EditedText("- leche\n- ", 10, 10))
        assertThat(MarkdownFormatting.continueListOnEnter("3. pan", 6)?.text).isEqualTo("3. pan\n4. ")
        assertThat(MarkdownFormatting.continueListOnEnter("- [x] leche", 11)?.text).isEqualTo("- [x] leche\n- [ ] ")
        assertThat(MarkdownFormatting.continueListOnEnter("  - sub", 7)?.text).isEqualTo("  - sub\n  - ")
    }

    /** Enter on an empty item ends the list; ordinary lines are left to the normal newline. */
    @Test
    fun `enter on an empty item ends the list`() {
        assertThat(MarkdownFormatting.continueListOnEnter("- leche\n- ", 10)).isEqualTo(EditedText("- leche\n", 8, 8))
        assertThat(MarkdownFormatting.continueListOnEnter("hola", 4)).isNull()
    }
}
