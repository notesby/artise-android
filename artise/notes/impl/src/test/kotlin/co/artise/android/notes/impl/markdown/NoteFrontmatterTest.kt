/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import com.google.common.truth.Truth.assertThat
import org.commonmark.node.ThematicBreak
import org.junit.Test

class NoteFrontmatterTest {
    /** The header a page carries is read: its type, aliases (inline or as a list) and summary. */
    @Test
    fun `header fields are read`() {
        val inline = NoteFrontmatter.parse("---\ntype: person\naliases: [Mamá, \"Ana María\"]\nsummary: Mamá de Hector\n---\n# Ana")
        assertThat(inline).isEqualTo(NoteFrontmatter("person", null, listOf("Mamá", "Ana María"), "Mamá de Hector", lineCount = 5))
        val listed = NoteFrontmatter.parse("---\ntitle: 'Ana'\naliases:\n  - Mamá\n  - Ana María\ntype: person\n---\n")
        assertThat(listed?.aliases).containsExactly("Mamá", "Ana María").inOrder()
        assertThat(listed?.title).isEqualTo("Ana")
    }

    /** A note without a header, or with a `---` rule further down, has no header. */
    @Test
    fun `no header is no header`() {
        assertThat(NoteFrontmatter.parse("# Ana\n---\ntype: person\n---")).isNull()
        assertThat(NoteFrontmatter.parse("---\ntype: person\n# never closed")).isNull()
    }

    /** The reading view hides the header but keeps its lines, so checklist line numbers still match the note's text. */
    @Test
    fun `header is blanked for reading without moving lines`() {
        val note = "---\ntype: person\n---\n- [ ] llamar"
        assertThat(NoteFrontmatter.blank(note)).isEqualTo("\n\n\n- [ ] llamar")
        val nodes = generateSequence(NoteMarkdownParser.parse(note).firstChild) { it.next }.toList()
        assertThat(nodes.filterIsInstance<ThematicBreak>()).isEmpty()
    }
}
