/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.impl.markdown.NoteLinkResolver
import co.artise.android.notes.impl.markdown.NoteMarkdownParser
import co.artise.android.notes.impl.markdown.WikiLinkRewriter
import org.commonmark.node.Link
import org.commonmark.node.Node
import java.net.URLDecoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The notes of a chat and the links between them, placed on a unit square. */
data class NotesGraphModel(
    val nodes: List<GraphNode>,
    /** Pairs of indexes into [nodes]; each link once, whichever note it's written in. */
    val edges: List<Pair<Int, Int>>,
)

/** A note on the map: [x] and [y] in 0..1, [degree] = how many notes it's linked with. */
data class GraphNode(
    val path: String,
    val name: String,
    val x: Float,
    val y: Float,
    val degree: Int,
)

/**
 * Builds the map from the notes on the phone, so it works offline and includes edits not sent yet.
 * Links are read like on the note screen: `[[wiki links]]` and Markdown links to other notes; photos are left out.
 */
object NotesGraphBuilder {
    fun build(files: List<LocalFile>): NotesGraphModel {
        val notes = files.filter { it.isNote && it.path.endsWith(".md") }.sortedBy { it.path.lowercase() }
        val paths = notes.map { it.path }
        val index = paths.withIndex().associate { (i, path) -> path to i }
        val edges = linkedSetOf<Pair<Int, Int>>()
        for (note in notes) {
            val from = index.getValue(note.path)
            val targets = linkTargets(note.content.orEmpty())
            for (target in targets) {
                val to = NoteLinkResolver.resolve(target, paths)?.let(index::get) ?: continue
                if (to != from) edges += min(from, to) to max(from, to)
            }
        }
        val edgeList = edges.toList()
        val positions = ForceLayout.layout(notes.size, edgeList)
        val degrees = IntArray(notes.size).also { degree ->
            edgeList.forEach { (a, b) ->
            degree[a]++
            degree[b]++
        }
        }
        return NotesGraphModel(
            nodes = notes.mapIndexed { i, note ->
                GraphNode(
                    path = note.path,
                    name = note.path.substringAfterLast('/').removeSuffix(".md"),
                    x = positions[2 * i],
                    y = positions[2 * i + 1],
                    degree = degrees[i],
                )
            },
            edges = edgeList,
        )
    }

    /** What each link in [markdown] points to: wiki link targets, and relative Markdown links to `.md` files. */
    internal fun linkTargets(markdown: String): List<String> {
        val targets = mutableListOf<String>()
        fun visit(node: Node) {
            if (node is Link) {
                val destination = node.destination
                val wiki = WikiLinkRewriter.targetOf(destination)
                when {
                    wiki != null -> targets += wiki
                    // A relative link to a note, e.g. [lista](Listas/S%C3%BAper.md).
                    !destination.contains("://") && destination.substringBefore('#').endsWith(".md") ->
                        targets += URLDecoder.decode(destination.substringBefore('#'), Charsets.UTF_8.name()).removePrefix("./")
                }
            }
            var child = node.firstChild
            while (child != null) {
                visit(child)
                child = child.next
            }
        }
        visit(NoteMarkdownParser.parse(markdown))
        return targets
    }
}

/**
 * A small force-directed layout (Fruchterman–Reingold): linked notes pull together, all notes push apart.
 * Starts from a fixed spiral, so the same notes always give the same map. Returns x0, y0, x1, y1… in 0..1.
 */
internal object ForceLayout {
    private const val ITERATIONS = 250

    fun layout(count: Int, edges: List<Pair<Int, Int>>): FloatArray {
        val pos = FloatArray(count * 2)
        if (count == 0) return pos
        if (count == 1) return floatArrayOf(0.5f, 0.5f)
        // Golden-angle spiral: evenly spread, deterministic.
        for (i in 0 until count) {
            val radius = sqrt((i + 0.5f) / count) * 0.5f
            val angle = i * 2.3999631f
            pos[2 * i] = 0.5f + radius * kotlin.math.cos(angle)
            pos[2 * i + 1] = 0.5f + radius * kotlin.math.sin(angle)
        }
        val k = sqrt(1f / count)
        val disp = FloatArray(count * 2)
        var temperature = 0.1f
        repeat(ITERATIONS) {
            disp.fill(0f)
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    val dx = pos[2 * i] - pos[2 * j]
                    val dy = pos[2 * i + 1] - pos[2 * j + 1]
                    val distance = max(sqrt(dx * dx + dy * dy), 0.001f)
                    val force = k * k / distance
                    disp[2 * i] += dx / distance * force
                    disp[2 * i + 1] += dy / distance * force
                    disp[2 * j] -= dx / distance * force
                    disp[2 * j + 1] -= dy / distance * force
                }
            }
            for ((a, b) in edges) {
                val dx = pos[2 * a] - pos[2 * b]
                val dy = pos[2 * a + 1] - pos[2 * b + 1]
                val distance = max(sqrt(dx * dx + dy * dy), 0.001f)
                val force = distance * distance / k
                disp[2 * a] -= dx / distance * force
                disp[2 * a + 1] -= dy / distance * force
                disp[2 * b] += dx / distance * force
                disp[2 * b + 1] += dy / distance * force
            }
            for (i in 0 until count) {
                val dx = disp[2 * i]
                val dy = disp[2 * i + 1]
                val length = max(sqrt(dx * dx + dy * dy), 0.001f)
                val step = min(length, temperature)
                pos[2 * i] += dx / length * step
                pos[2 * i + 1] += dy / length * step
            }
            temperature *= 0.98f
        }
        return normalize(pos)
    }

    /** Scales the layout to fill 0.05..0.95, keeping its proportions. */
    private fun normalize(pos: FloatArray): FloatArray {
        val xs = pos.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = pos.filterIndexed { i, _ -> i % 2 == 1 }
        val minX = xs.min()
        val minY = ys.min()
        val span = max(max(xs.max() - minX, ys.max() - minY), 0.0001f)
        val offsetX = (span - (xs.max() - minX)) / 2
        val offsetY = (span - (ys.max() - minY)) / 2
        return FloatArray(pos.size) { i ->
            val value = if (i % 2 == 0) (pos[i] - minX + offsetX) / span else (pos[i] - minY + offsetY) / span
            0.05f + value * 0.9f
        }
    }
}
