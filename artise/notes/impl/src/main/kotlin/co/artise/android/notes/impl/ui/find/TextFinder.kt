/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.find

import java.text.Normalizer

/** One match: on [page] (from 0), the characters [start] until [end] of that page's text. */
data class FindMatch(val page: Int, val start: Int, val end: Int)

/** Finds text the way people search: without caring about case or accents ("camion" finds "camión"). */
object TextFinder {
    /**
     * Every match of [query] in [pages], page by page and in reading order. Blank queries match nothing.
     * Matches cover exactly as many characters as the query, so they can be highlighted in the original text.
     */
    fun find(query: String, pages: List<String>): List<FindMatch> {
        val needle = fold(query)
        if (needle.isBlank()) return emptyList()
        val matches = mutableListOf<FindMatch>()
        pages.forEachIndexed { page, text ->
            val haystack = fold(text)
            var from = haystack.indexOf(needle)
            while (from >= 0) {
                matches += FindMatch(page, from, from + needle.length)
                from = haystack.indexOf(needle, from + 1)
            }
        }
        return matches
    }

    /** The match after [current], back to the first after the last. */
    fun next(current: Int, count: Int): Int = if (count == 0) 0 else (current + 1) % count

    /** The match before [current], round to the last before the first. */
    fun previous(current: Int, count: Int): Int = if (count == 0) 0 else (current - 1 + count) % count

    /** A little of the text around [match] on one line, for when the page can't show the highlight itself. */
    fun snippet(text: String, match: FindMatch, radius: Int = SNIPPET_RADIUS): String {
        val from = (match.start - radius).coerceAtLeast(0)
        val to = (match.end + radius).coerceAtMost(text.length)
        val core = text.substring(from, to).replace(Regex("\\s+"), " ").trim()
        val before = if (from > 0) "…" else ""
        val after = if (to < text.length) "…" else ""
        return before + core + after
    }

    /**
     * Lower case without accents, one character for one character, so positions in the result are positions in [text].
     * A letter that decomposes ("á" = "a" + accent) keeps its base letter only.
     */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (char in text) {
            val lower = char.lowercaseChar()
            val decomposed = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
            out.append(decomposed.firstOrNull { !it.isCombiningMark() } ?: lower)
        }
        return out.toString()
    }

    private fun Char.isCombiningMark(): Boolean = Character.getType(this).toByte() in COMBINING_TYPES

    private val COMBINING_TYPES = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)
    private const val SNIPPET_RADIUS = 40
}
