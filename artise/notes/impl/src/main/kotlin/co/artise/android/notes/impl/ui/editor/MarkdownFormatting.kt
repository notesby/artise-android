/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

/** A formatting button in the editor's toolbar. */
enum class FormatAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    HEADING,
    BULLET_LIST,
    NUMBERED_LIST,
    CHECKLIST,
    QUOTE,
    NOTE_LINK,
}

/** Text with a selection: [start] == [end] is a cursor. */
data class EditedText(val text: String, val start: Int, val end: Int)

/**
 * The Markdown each toolbar button writes, so people get formatting without learning the syntax.
 * Every action toggles: pressing a button again on formatted text removes the formatting.
 */
object MarkdownFormatting {
    fun apply(action: FormatAction, text: String, start: Int, end: Int): EditedText {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        return when (action) {
            FormatAction.BOLD -> toggleWrap(text, from, to, "**")
            FormatAction.ITALIC -> toggleWrap(text, from, to, "*")
            FormatAction.STRIKETHROUGH -> toggleWrap(text, from, to, "~~")
            FormatAction.HEADING -> cycleHeading(text, from, to)
            FormatAction.BULLET_LIST -> toggleLinePrefix(text, from, to, LineKind.BULLET)
            FormatAction.NUMBERED_LIST -> toggleLinePrefix(text, from, to, LineKind.NUMBERED)
            FormatAction.CHECKLIST -> toggleLinePrefix(text, from, to, LineKind.CHECKLIST)
            FormatAction.QUOTE -> toggleLinePrefix(text, from, to, LineKind.QUOTE)
            // "[[" opens the note suggestions; choosing one completes the link.
            FormatAction.NOTE_LINK -> EditedText(text.substring(0, from) + "[[" + text.substring(to), from + 2, from + 2)
        }
    }

    /** `**` around the selection, or removed if already there; with no selection, an empty pair to type into. */
    private fun toggleWrap(text: String, from: Int, to: Int, marker: String): EditedText {
        val m = marker.length
        val wrapped = from >= m && to + m <= text.length &&
            text.substring(from - m, from) == marker && text.substring(to, to + m) == marker &&
            // "*" inside "**": the italic button shouldn't eat half of a bold marker.
            !(marker == "*" && (text.getOrNull(from - m - 1) == '*' || text.getOrNull(to + m) == '*'))
        return if (wrapped) {
            EditedText(text.removeRange(to, to + m).removeRange(from - m, from), from - m, to - m)
        } else {
            EditedText(text.substring(0, from) + marker + text.substring(from, to) + marker + text.substring(to), from + m, to + m)
        }
    }

    /** No heading → # → ## → ### → no heading, on the line with the cursor. */
    private fun cycleHeading(text: String, from: Int, to: Int): EditedText {
        val lineStart = text.lastIndexOf('\n', from - 1) + 1
        val level = text.substring(lineStart).takeWhile { it == '#' }.length
        val hasSpace = text.getOrNull(lineStart + level) == ' '
        val oldPrefix = if (level > 0 && hasSpace) level + 1 else 0
        val newPrefix = when (if (oldPrefix == 0) 0 else level) {
            0 -> "# "
            1 -> "## "
            2 -> "### "
            else -> ""
        }
        val newText = text.substring(0, lineStart) + newPrefix + text.substring(lineStart + oldPrefix)
        val shift = newPrefix.length - oldPrefix
        return EditedText(newText, (from + shift).coerceAtLeast(lineStart), (to + shift).coerceAtLeast(lineStart))
    }

    /** Lists and quotes apply to every line the selection touches; if they all have it already, it's removed. */
    private fun toggleLinePrefix(text: String, from: Int, to: Int, kind: LineKind): EditedText {
        val firstLineStart = text.lastIndexOf('\n', from - 1) + 1
        val lastLineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
        val lines = text.substring(firstLineStart, lastLineEnd).split('\n')
        val allHaveIt = lines.all { LineKind.of(it) == kind }
        var number = 0
        val newLines = lines.map { line ->
            val body = line.substring(LineKind.prefixLength(line))
            when {
                allHaveIt -> body
                // Blank lines inside a selection stay blank rather than becoming empty items.
                line.isBlank() && lines.size > 1 -> line
                else -> kind.prefix(++number) + body
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, firstLineStart) + newBlock + text.substring(lastLineEnd)
        return if (from == to) {
            // Keep the cursor at the same place in the line's text.
            val shift = newLines.first().length - lines.first().length
            val cursor = (from + shift).coerceIn(firstLineStart, firstLineStart + newLines.first().length)
            EditedText(newText, cursor, cursor)
        } else {
            EditedText(newText, firstLineStart, firstLineStart + newBlock.length)
        }
    }

    /**
     * What Enter does on a list or quote line: continue it ("- ", the next number, an unticked "- [ ] "),
     * or, on an item left empty, end the list by clearing its marker. `null` for ordinary lines.
     * [cursor] is where the newline is being typed.
     */
    fun continueListOnEnter(text: String, cursor: Int): EditedText? {
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val line = text.substring(lineStart, cursor)
        val kind = LineKind.of(line) ?: return null
        val prefixLength = LineKind.prefixLength(line)
        val indent = line.takeWhile { it == ' ' }
        if (line.substring(prefixLength).isBlank()) {
            // An empty item: Enter ends the list.
            return EditedText(text.substring(0, lineStart) + text.substring(cursor), lineStart, lineStart)
        }
        val next = indent + when (kind) {
            LineKind.NUMBERED -> "${(Regex("\\d+").find(line)?.value?.toIntOrNull() ?: 0) + 1}. "
            else -> kind.prefix(1)
        }
        val inserted = "\n" + next
        return EditedText(text.substring(0, cursor) + inserted + text.substring(cursor), cursor + inserted.length, cursor + inserted.length)
    }
}

/** The kinds of line the toolbar writes, recognised by their Markdown prefix. */
internal enum class LineKind {
    BULLET,
    NUMBERED,
    CHECKLIST,
    QUOTE;

    fun prefix(number: Int): String = when (this) {
        BULLET -> "- "
        NUMBERED -> "$number. "
        CHECKLIST -> "- [ ] "
        QUOTE -> "> "
    }

    companion object {
        private val CHECKLIST_PREFIX = Regex("^\\s*[-*+] \\[[ xX]] ")
        private val BULLET_PREFIX = Regex("^\\s*[-*+] ")
        private val NUMBERED_PREFIX = Regex("^\\s*\\d+[.)] ")
        private val QUOTE_PREFIX = Regex("^\\s*> ?")

        fun of(line: String): LineKind? = when {
            CHECKLIST_PREFIX.containsMatchIn(line) -> CHECKLIST
            BULLET_PREFIX.containsMatchIn(line) -> BULLET
            NUMBERED_PREFIX.containsMatchIn(line) -> NUMBERED
            QUOTE_PREFIX.containsMatchIn(line) -> QUOTE
            else -> null
        }

        /** Length of the list or quote marker at the start of [line], indentation included; 0 if none. */
        fun prefixLength(line: String): Int =
            listOf(CHECKLIST_PREFIX, BULLET_PREFIX, NUMBERED_PREFIX, QUOTE_PREFIX).firstNotNullOfOrNull { it.find(line) }?.value?.length ?: 0
    }
}
