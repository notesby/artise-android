/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import com.google.common.truth.Truth.assertThat
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.junit.Test

class NoteMarkdownParserTest {
    private fun Node.all(): List<Node> = generateSequence(firstChild) { it.next }.flatMap { sequenceOf(it) + it.all() }.toList()

    /** Checklists keep their ticked state, as families use notes for shopping lists. */
    @Test
    fun `checklists are parsed with their state`() {
        val markers = NoteMarkdownParser.parse("- [x] leche\n- [ ] pan").all().filterIsInstance<TaskListItemMarker>()
        assertThat(markers.map { it.isChecked }).containsExactly(true, false).inOrder()
    }

    /** Wiki links arrive as links the view can route back to a note. */
    @Test
    fun `wiki links become note links`() {
        val link = NoteMarkdownParser.parse("Ver [[Recetas/Mole|el mole]]").all().filterIsInstance<Link>().single()
        assertThat(WikiLinkRewriter.targetOf(link.destination)).isEqualTo("Recetas/Mole")
    }

    /** Tables are recognised, so they're drawn as rows rather than raw pipes. */
    @Test
    fun `tables are parsed`() {
        assertThat(NoteMarkdownParser.parse("| a | b |\n| --- | --- |\n| 1 | 2 |").all().filterIsInstance<TableBlock>()).hasSize(1)
    }
}
