/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

/**
 * The header Obsidian reads at the top of a note, between two `---` lines:
 * ```
 * ---
 * type: person
 * aliases: [Mamá, Ana María]
 * summary: Ana, Hector's mother
 * ---
 * ```
 * Only the keys a page uses are read ([type], [title], [aliases], [summary]); anything else is kept as text and ignored.
 */
data class NoteFrontmatter(
    val type: String?,
    val title: String?,
    val aliases: List<String>,
    val summary: String?,
    /** How many lines the header takes, closing `---` included. */
    val lineCount: Int,
) {
    companion object {
        private val FENCE = Regex("^---\\s*$")
        private val KEY_VALUE = Regex("^([A-Za-z_][A-Za-z0-9_-]*):\\s*(.*)$")
        private val LIST_ITEM = Regex("^\\s*-\\s+(.*)$")
        private val QUOTES = setOf('"', '\'')

        /** The header of [markdown], or `null` when it doesn't start with one. */
        fun parse(markdown: String): NoteFrontmatter? {
            val lines = markdown.lines()
            if (lines.isEmpty() || !FENCE.matches(lines[0])) return null
            val end = (1 until lines.size).firstOrNull { FENCE.matches(lines[it]) } ?: return null
            val values = mutableMapOf<String, String>()
            val lists = mutableMapOf<String, MutableList<String>>()
            var openList: MutableList<String>? = null
            for (line in lines.subList(1, end)) {
                val item = openList?.let { LIST_ITEM.matchEntire(line) }
                val pair = KEY_VALUE.matchEntire(line)
                when {
                    // "- Mamá" under an "aliases:" line.
                    item != null -> openList?.add(unquote(item.groupValues[1]))
                    pair == null -> Unit
                    // "aliases:" with nothing after it starts a list.
                    pair.groupValues[2].isBlank() -> openList = mutableListOf<String>().also { lists[pair.groupValues[1].lowercase()] = it }
                    else -> {
                        values[pair.groupValues[1].lowercase()] = pair.groupValues[2].trim()
                        openList = null
                    }
                }
            }
            return NoteFrontmatter(
                type = values["type"]?.let(::unquote)?.ifEmpty { null },
                title = values["title"]?.let(::unquote)?.ifEmpty { null },
                aliases = lists["aliases"] ?: values["aliases"]?.let(::inlineList).orEmpty(),
                summary = values["summary"]?.let(::unquote)?.ifEmpty { null },
                lineCount = end + 1,
            )
        }

        /**
         * [markdown] with its header's lines emptied, so the reading view doesn't show it as text. Line numbers stay
         * the same, which the checklist toggles rely on.
         */
        fun blank(markdown: String): String {
            val header = parse(markdown) ?: return markdown
            val lines = markdown.lines()
            return (List(header.lineCount) { "" } + lines.drop(header.lineCount)).joinToString("\n")
        }

        /** `[Mamá, "Ana María"]` or `Mamá, Ana María` → the names. */
        private fun inlineList(value: String): List<String> =
            value.removePrefix("[").removeSuffix("]").split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }

        private fun unquote(value: String): String {
            val trimmed = value.trim()
            val quoted = trimmed.length >= 2 && trimmed.first() == trimmed.last() && trimmed.first() in QUOTES
            return if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed
        }
    }
}
