/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

/** Ticks and unticks checklist items in a note's Markdown: `- [ ] pan` ⇄ `- [x] pan`. */
object ChecklistToggle {
    private val TASK = Regex("^(\\s*(?:[-*+]|\\d+[.)]) \\[)([ xX])(])")

    /** [markdown] with the checklist item on line [lineIndex] (from 0) flipped, or `null` if that line isn't one. */
    fun toggle(markdown: String, lineIndex: Int): String? {
        val lines = markdown.split('\n').toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return null
        val match = TASK.find(line) ?: return null
        val mark = if (match.groupValues[2].isBlank()) "x" else " "
        lines[lineIndex] = match.groupValues[1] + mark + match.groupValues[3] + line.substring(match.range.last + 1)
        return lines.joinToString("\n")
    }
}
