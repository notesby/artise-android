/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** The looks live preview uses; built from the theme in the view, and simple stand-ins in tests. */
@Immutable
data class LivePreviewStyles(
    val heading1: SpanStyle,
    val heading2: SpanStyle,
    val heading3: SpanStyle,
    val bold: SpanStyle,
    val italic: SpanStyle,
    val strikethrough: SpanStyle,
    val code: SpanStyle,
    val link: SpanStyle,
    /** Secondary text: ticked items, quotes, list numbers, table pipes. */
    val dim: SpanStyle,
    /** The ☐/☑ drawn for checklist items: larger than the text so it's easy to see and tap. */
    val checkbox: SpanStyle,
)

/** Something on a formatted line that a tap acts on instead of placing the cursor. */
sealed interface LivePreviewHit {
    /** The checkbox of the checklist item on [lineIndex]. */
    data class Checkbox(val lineIndex: Int) : LivePreviewHit

    /** A link spanning [start] until [end] in the Markdown. */
    data class Link(val start: Int, val end: Int, val link: EditableLink) : LivePreviewHit
}

/** A link as the link dialog edits it. [isNote] = `[[note]]`; otherwise `[text](address)`. */
data class EditableLink(
    val isNote: Boolean,
    /** The note (for `[[...]]`, heading included) or web address. */
    val target: String,
    /** The text shown instead of the target, or empty for none. */
    val shownText: String,
) {
    /** The Markdown for this link. */
    fun toMarkdown(): String = when {
        isNote && shownText.isBlank() -> "[[${target.trim()}]]"
        isNote -> "[[${target.trim()}|${shownText.trim()}]]"
        // A web address with no text of its own is written as is: it shows as a link anyway.
        shownText.isBlank() -> target.trim()
        else -> "[${shownText.trim()}](${target.trim()})"
    }

    /** What remains when the link is removed: the text people saw. */
    fun label(): String = shownText.ifBlank { if (isNote) target.substringBefore('#').substringAfterLast('/') else target }
}

/**
 * Obsidian-style live preview for the editor: every line shows formatted, with its Markdown marks hidden,
 * except the lines being edited ([rawLines]), which show exactly as written. A code block or table the cursor
 * is in shows raw as a whole. The text itself never changes; only how it's drawn.
 */
object LivePreview {
    private val HEADING = Regex("^(#{1,6}) ")
    private val CHECKLIST = Regex("^(\\s*)([-*+]) \\[([ xX])] ")
    private val BULLET = Regex("^(\\s*)([-*+]) ")
    private val NUMBERED = Regex("^(\\s*)(\\d+[.)]) ")
    private val QUOTE = Regex("^(\\s*)> ?")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val FENCE = Regex("^\\s{0,3}(```|~~~)")
    private val TABLE_ROW = Regex("^\\s*\\|.*\\|\\s*$")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

    private val INLINE_CODE = Regex("`([^`\\n]+)`")
    private val WIKI_LINK = Regex("(!?)\\[\\[([^\\[\\]\\n]+)]]")
    private val MARKDOWN_LINK = Regex("\\[([^\\[\\]\\n]+)]\\(([^()\\s]+)\\)")

    /** A web address typed as plain text; trailing punctuation isn't part of it. */
    private val BARE_URL = Regex("(?<![\\w/(<\\[])(?:https?://|www\\.)[^\\s<>()\\[\\]]*[^\\s<>()\\[\\].,;:!?'\"]")
    private val BOLD = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val ITALIC_STAR = Regex("(?<!\\*)\\*(?=[^\\s*])([^*\\n]+?)(?<=\\S)\\*(?!\\*)")
    private val ITALIC_UNDERSCORE = Regex("(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])")

    /** The lines shown raw for this selection: the selected lines, grown to a whole code block or table. */
    fun rawLines(text: String, selectionStart: Int, selectionEnd: Int): IntRange {
        val lines = text.split('\n')
        val first = lineOf(text, minOf(selectionStart, selectionEnd))
        val last = lineOf(text, maxOf(selectionStart, selectionEnd))
        val blocks = blocksOf(lines)
        val grow = blocks.filter { it.first <= last && it.last >= first }
        return (grow.minOfOrNull { it.first }?.coerceAtMost(first) ?: first)..(grow.maxOfOrNull { it.last }?.coerceAtLeast(last) ?: last)
    }

    /** The formatted text for [text], with [rawLines] left as written, and the mapping between the two. */
    fun transform(text: String, rawLines: IntRange, styles: LivePreviewStyles): TransformedText {
        val builder = Builder(text)
        val lines = text.split('\n')
        val blocks = blocksOf(lines)
        var lineStart = 0
        lines.forEachIndexed { index, line ->
            val lineEnd = lineStart + line.length
            val block = blocks.firstOrNull { index in it }
            when {
                index in rawLines -> builder.keep(lineEnd)
                block != null && FENCE.containsMatchIn(
                    lines[block.first]
                ) -> codeLine(builder, lineEnd, isFence = index == block.first || index == block.last, styles)
                block != null -> tableLine(builder, line, lineStart, lineEnd, isHeader = index == block.first, styles)
                else -> formattedLine(builder, line, lineStart, lineEnd, styles)
            }
            if (index < lines.lastIndex) builder.keep(lineEnd + 1)
            lineStart = lineEnd + 1
        }
        return builder.build()
    }

    /** What a tap at [offset] (in the Markdown) lands on, for a line that was showing formatted. */
    fun hitAt(text: String, offset: Int): LivePreviewHit? {
        val index = lineOf(text, offset)
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val line = text.substring(lineStart).substringBefore('\n')
        val column = offset - lineStart
        CHECKLIST.find(line)?.let { match ->
            val markerStart = match.groupValues[1].length
            if (column in markerStart until match.range.last + 1) return LivePreviewHit.Checkbox(index)
        }
        for (match in WIKI_LINK.findAll(line)) {
            val (labelStart, labelEnd) = wikiLabelRange(match)
            if (column in labelStart until labelEnd) {
                val inner = match.groupValues[2]
                val link = EditableLink(isNote = true, target = inner.substringBefore('|'), shownText = inner.substringAfter('|', ""))
                return LivePreviewHit.Link(lineStart + match.range.first, lineStart + match.range.last + 1, link)
            }
        }
        for (match in MARKDOWN_LINK.findAll(line)) {
            val labelStart = match.range.first + 1
            if (column in labelStart until labelStart + match.groupValues[1].length) {
                val link = EditableLink(isNote = false, target = match.groupValues[2], shownText = match.groupValues[1])
                return LivePreviewHit.Link(lineStart + match.range.first, lineStart + match.range.last + 1, link)
            }
        }
        for (match in BARE_URL.findAll(line)) {
            // Inside a [text](address) link, the address is part of that link, handled above.
            if (MARKDOWN_LINK.findAll(line).any { match.range.first in it.range }) continue
            if (column in match.range.first until match.range.last + 1) {
                val link = EditableLink(isNote = false, target = match.value, shownText = "")
                return LivePreviewHit.Link(lineStart + match.range.first, lineStart + match.range.last + 1, link)
            }
        }
        return null
    }

    /** Index of the line holding [offset]. */
    fun lineOf(text: String, offset: Int): Int = text.subSequence(0, offset.coerceIn(0, text.length)).count { it == '\n' }

    /** Code blocks (fence to fence) and tables (a header row, then a separator row), as line ranges. */
    private fun blocksOf(lines: List<String>): List<IntRange> {
        val blocks = mutableListOf<IntRange>()
        var i = 0
        while (i < lines.size) {
            when {
                FENCE.containsMatchIn(lines[i]) -> {
                    val end = (i + 1 until lines.size).firstOrNull { FENCE.containsMatchIn(lines[it]) } ?: lines.lastIndex
                    blocks += i..end
                    i = end + 1
                }
                TABLE_ROW.containsMatchIn(lines[i]) && lines.getOrNull(i + 1)?.let { TABLE_SEPARATOR.containsMatchIn(it) } == true -> {
                    var end = i + 1
                    while (end + 1 < lines.size && TABLE_ROW.containsMatchIn(lines[end + 1])) end++
                    blocks += i..end
                    i = end + 1
                }
                else -> i++
            }
        }
        return blocks
    }

    private fun codeLine(builder: Builder, end: Int, isFence: Boolean, styles: LivePreviewStyles) {
        if (isFence) {
            // The ``` lines disappear; the empty line keeps the block apart from the text around it.
            builder.hide(end)
        } else {
            builder.styled(styles.code) { builder.keep(end) }
        }
    }

    private fun tableLine(builder: Builder, line: String, start: Int, end: Int, isHeader: Boolean, styles: LivePreviewStyles) {
        val rowStyle = if (isHeader) styles.code.merge(styles.bold) else styles.code
        builder.styled(rowStyle) {
            if (TABLE_SEPARATOR.containsMatchIn(line)) {
                builder.styled(styles.dim) { builder.keep(end) }
            } else {
                line.forEachIndexed { i, char ->
                    if (char == '|') builder.styled(styles.dim) { builder.keep(start + i + 1) } else builder.keep(start + i + 1)
                }
            }
        }
    }

    private fun formattedLine(builder: Builder, line: String, start: Int, end: Int, styles: LivePreviewStyles) {
        HEADING.find(line)?.let { match ->
            builder.hide(start + match.range.last + 1)
            val style = when (match.groupValues[1].length) {
                1 -> styles.heading1
                2 -> styles.heading2
                else -> styles.heading3
            }
            builder.styled(style) { inline(builder, line, start, start + match.range.last + 1, end, styles) }
            return
        }
        CHECKLIST.find(line)?.let { match ->
            val indent = match.groupValues[1].length
            builder.keep(start + indent)
            val checked = match.groupValues[3].isNotBlank()
            // Taps on the box map inside "[ ]", which is how hitAt knows the box was tapped.
            builder.styled(styles.checkbox) { builder.replace(start + match.range.last + 1, if (checked) "☑ " else "☐ ", mapTo = start + indent + 3) }
            if (checked) {
                builder.styled(styles.dim) { inline(builder, line, start, start + match.range.last + 1, end, styles) }
            } else {
                inline(builder, line, start, start + match.range.last + 1, end, styles)
            }
            return
        }
        BULLET.find(line)?.let { match ->
            val indent = match.groupValues[1].length
            builder.keep(start + indent)
            builder.replace(start + match.range.last + 1, "• ", mapTo = start + indent)
            inline(builder, line, start, start + match.range.last + 1, end, styles)
            return
        }
        NUMBERED.find(line)?.let { match ->
            builder.styled(styles.dim) { builder.keep(start + match.range.last + 1) }
            inline(builder, line, start, start + match.range.last + 1, end, styles)
            return
        }
        if (RULE.containsMatchIn(line)) {
            builder.styled(styles.dim) { builder.replace(end, "────────", mapTo = start) }
            return
        }
        QUOTE.find(line)?.let { match ->
            builder.keep(start + match.groupValues[1].length)
            builder.styled(styles.dim) {
                builder.replace(start + match.range.last + 1, "▍ ", mapTo = start + match.groupValues[1].length)
                builder.styled(styles.italic) { inline(builder, line, start, start + match.range.last + 1, end, styles) }
            }
            return
        }
        inline(builder, line, start, start, end, styles)
    }

    /** Inline formatting between [from] and [to] (absolute), [line] starting at [lineStart]. */
    private fun inline(builder: Builder, line: String, lineStart: Int, from: Int, to: Int, styles: LivePreviewStyles) {
        var i = from
        while (i < to) {
            val column = i - lineStart
            val match = listOf(INLINE_CODE, WIKI_LINK, MARKDOWN_LINK, BARE_URL, BOLD, STRIKE, ITALIC_STAR, ITALIC_UNDERSCORE)
                .firstNotNullOfOrNull { regex -> regex.matchAt(line, column)?.takeIf { lineStart + it.range.last < to }?.let { regex to it } }
            if (match == null) {
                builder.keep(i + 1)
                i++
                continue
            }
            val (regex, found) = match
            val matchStart = lineStart + found.range.first
            val matchEnd = lineStart + found.range.last + 1
            when (regex) {
                INLINE_CODE -> {
                    builder.hide(matchStart + 1)
                    builder.styled(styles.code) { builder.keep(matchEnd - 1) }
                    builder.hide(matchEnd)
                }
                WIKI_LINK -> {
                    val (labelStart, labelEnd) = wikiLabelRange(found)
                    builder.hide(lineStart + labelStart)
                    // An embedded photo or note (![[...]]) is marked so it isn't mistaken for text.
                    if (found.groupValues[1].isNotEmpty()) builder.replace(lineStart + labelStart, "🖼 ", mapTo = lineStart + labelStart)
                    builder.styled(styles.link) { builder.keep(lineStart + labelEnd) }
                    builder.hide(matchEnd)
                }
                BARE_URL -> builder.styled(styles.link) { builder.keep(matchEnd) }
                MARKDOWN_LINK -> {
                    builder.hide(matchStart + 1)
                    builder.styled(styles.link) { builder.keep(matchStart + 1 + found.groupValues[1].length) }
                    builder.hide(matchEnd)
                }
                else -> {
                    val marker = if (regex == BOLD || regex == STRIKE) 2 else 1
                    val style = when (regex) {
                        BOLD -> styles.bold
                        STRIKE -> styles.strikethrough
                        else -> styles.italic
                    }
                    builder.hide(matchStart + marker)
                    builder.styled(style) { inline(builder, line, lineStart, matchStart + marker, matchEnd - marker, styles) }
                    builder.hide(matchEnd)
                }
            }
            i = matchEnd
        }
    }

    /** Where a wiki link's visible text sits in its line: the shown text, else the note's name without folder or heading. */
    private fun wikiLabelRange(match: MatchResult): Pair<Int, Int> {
        val innerStart = match.range.first + match.groupValues[1].length + 2
        val inner = match.groupValues[2]
        val pipe = inner.indexOf('|')
        if (pipe >= 0) return innerStart + pipe + 1 to innerStart + inner.length
        val target = inner.substringBefore('#')
        val nameStart = target.lastIndexOf('/') + 1
        return innerStart + nameStart to innerStart + target.length
    }

    /**
     * Builds the formatted text while recording, for every position, where it is in the Markdown and back,
     * so the cursor and taps land on the right character even with marks hidden.
     */
    private class Builder(private val original: String) {
        private val out = StringBuilder()
        private val originalToOut = IntArray(original.length + 1)
        private val outToOriginal = ArrayList<Int>(original.length + 1)
        private val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        private var position = 0

        /** Shows the Markdown as written up to [until]. */
        fun keep(until: Int) {
            while (position < until) {
                originalToOut[position] = out.length
                outToOriginal += position
                out.append(original[position])
                position++
            }
        }

        /** Hides the Markdown up to [until]. */
        fun hide(until: Int) {
            while (position < until) {
                originalToOut[position] = out.length
                position++
            }
        }

        /** Shows [glyph] instead of the Markdown up to [until]; positions inside the glyph map to [mapTo]. */
        fun replace(until: Int, glyph: String, mapTo: Int) {
            // The replaced Markdown points at the glyph's start, so the cursor never lands inside the glyph.
            hide(until)
            glyph.forEach {
                outToOriginal += mapTo
                out.append(it)
            }
        }

        /** Applies [style] to everything [block] adds. */
        fun styled(style: SpanStyle, block: () -> Unit) {
            val from = out.length
            block()
            if (out.length > from) spans += AnnotatedString.Range(style, from, out.length)
        }

        fun build(): TransformedText {
            keep(original.length)
            originalToOut[original.length] = out.length
            outToOriginal += original.length
            val toOut = originalToOut.copyOf()
            val toOriginal = outToOriginal.toIntArray()
            return TransformedText(
                text = AnnotatedString(out.toString(), spans),
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = toOut[offset.coerceIn(0, toOut.lastIndex)]

                    override fun transformedToOriginal(offset: Int): Int = toOriginal[offset.coerceIn(0, toOriginal.lastIndex)]
                },
            )
        }
    }
}

/** The editor's live preview as a text field transformation: formatted everywhere except [rawLines]. */
class LivePreviewTransformation(
    private val rawLines: IntRange,
    private val styles: LivePreviewStyles,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = LivePreview.transform(text.text, rawLines, styles)

    // Compose compares transformations to know when to redraw: equal while the raw lines and looks are the same.
    override fun equals(other: Any?) = other is LivePreviewTransformation && other.rawLines == rawLines && other.styles == styles

    override fun hashCode() = 31 * rawLines.hashCode() + styles.hashCode()
}
