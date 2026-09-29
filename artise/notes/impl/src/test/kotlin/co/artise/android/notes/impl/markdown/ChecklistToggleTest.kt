/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import com.google.common.truth.Truth.assertThat
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.junit.Test

class ChecklistToggleTest {
    private val note = "# Súper\n- [ ] leche\n- [x] pan\n  - [ ] integral\n1. [ ] pagar\n- normal"

    /** Tapping an unticked item ticks it, and a ticked one unticks, changing only that line. */
    @Test
    fun `items flip on their line only`() {
        assertThat(ChecklistToggle.toggle(note, 1)).isEqualTo(note.replace("- [ ] leche", "- [x] leche"))
        assertThat(ChecklistToggle.toggle(note, 2)).isEqualTo(note.replace("- [x] pan", "- [ ] pan"))
    }

    /** Nested and numbered checklist items work too; a capital X counts as ticked. */
    @Test
    fun `nested numbered and capital items`() {
        assertThat(ChecklistToggle.toggle(note, 3)).contains("  - [x] integral")
        assertThat(ChecklistToggle.toggle(note, 4)).contains("1. [x] pagar")
        assertThat(ChecklistToggle.toggle("- [X] listo", 0)).isEqualTo("- [ ] listo")
    }

    /** Lines that aren't checklist items, or lines that don't exist, change nothing. */
    @Test
    fun `other lines are refused`() {
        assertThat(ChecklistToggle.toggle(note, 0)).isNull()
        assertThat(ChecklistToggle.toggle(note, 5)).isNull()
        assertThat(ChecklistToggle.toggle(note, 42)).isNull()
    }

    /** The parser reports each item's line in the note as written, even after [[links]] are rewritten. */
    @Test
    fun `parsed items know their line`() {
        fun Node.all(): List<Node> = generateSequence(firstChild) { it.next }.flatMap { sequenceOf(it) + it.all() }.toList()
        val items = NoteMarkdownParser.parse("Ver [[Mole]]\n\n- [ ] leche\n- [ ] [[Pan]]").all().filterIsInstance<ListItem>()
        assertThat(items.map { it.sourceSpans.first().lineIndex }).containsExactly(2, 3).inOrder()
    }
}
