/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.impl.ui.aNote
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.hypot

class NotesGraphBuilderTest {
    private val files = listOf(
        aNote("Súper.md", "Para el [[Mole]] y [la receta](Recetas/Sopa.md)."),
        aNote("Recetas/Mole.md", "Comprar en [[Súper]]. Ver ![[luna.jpg]] y [web](https://artise.co/a.md)."),
        aNote("Recetas/Sopa.md", "Sin enlaces."),
        aNote("Ideas.md", "[[Ideas]] y [[No existe]]"),
        LocalFile("Fotos/luna.jpg", "v", 1, 1, isNote = false, content = null, hasLocalEdits = false),
    )

    /** Wiki links and relative Markdown links count; a link both ways is one edge; photos, web links and self-links don't. */
    @Test
    fun `links become edges once`() {
        val graph = NotesGraphBuilder.build(files)
        val names = graph.nodes.map { it.name }
        assertThat(names).containsExactly("Ideas", "Mole", "Sopa", "Súper")
        val edges = graph.edges.map { (a, b) -> setOf(names[a], names[b]) }
        assertThat(edges).containsExactly(setOf("Súper", "Mole"), setOf("Súper", "Sopa"))
        assertThat(graph.nodes.first { it.name == "Súper" }.degree).isEqualTo(2)
        assertThat(graph.nodes.first { it.name == "Ideas" }.degree).isEqualTo(0)
    }

    /** Every note sits inside the map, and the same notes always give the same map. */
    @Test
    fun `layout is inside the map and stable`() {
        val first = NotesGraphBuilder.build(files)
        assertThat(first.nodes.all { it.x in 0f..1f && it.y in 0f..1f }).isTrue()
        assertThat(NotesGraphBuilder.build(files)).isEqualTo(first)
    }

    /** Linked notes end up closer together than unlinked ones, which is what makes the map readable. */
    @Test
    fun `linked notes are closer`() {
        val chain = (1..6).map { aNote("N$it.md", if (it < 6) "[[N${it + 1}]]" else "") } + aNote("Solo.md", "")
        val graph = NotesGraphBuilder.build(chain)
        fun distance(a: String, b: String): Double {
            val na = graph.nodes.first { it.name == a }
            val nb = graph.nodes.first { it.name == b }
            return hypot((na.x - nb.x).toDouble(), (na.y - nb.y).toDouble())
        }
        assertThat(distance("N1", "N2")).isLessThan(distance("N1", "N6"))
    }

    /** No notes, or one note, still gives a valid map. */
    @Test
    fun `small maps work`() {
        assertThat(NotesGraphBuilder.build(emptyList()).nodes).isEmpty()
        val single = NotesGraphBuilder.build(listOf(aNote("Solo.md", "")))
        assertThat(single.nodes.single().x).isEqualTo(0.5f)
    }

    /**
     * A few linked notes plus several with no links (like the demo chat): the linked ones stay readable, spread out
     * rather than piled in the middle while the loose ones sit at the edges.
     */
    @Test
    fun `linked notes are spread out even with loose notes around`() {
        // 0-6 linked around 0 and to each other; 7-11 have no links.
        val edges = listOf(0 to 1, 0 to 2, 0 to 3, 0 to 4, 1 to 2, 3 to 4, 4 to 5, 5 to 6, 2 to 6)
        val pos = ForceLayout.layout(12, edges)
        fun distance(a: Int, b: Int) = kotlin.math.hypot(pos[2 * a] - pos[2 * b], pos[2 * a + 1] - pos[2 * b + 1])
        val closest = (0 until 12).flatMap { a -> (a + 1 until 12).map { b -> distance(a, b) } }.min()
        // Every pair of notes is at least 8% of the map apart: room for their names.
        com.google.common.truth.Truth.assertThat(closest).isGreaterThan(0.08f)
    }
}
