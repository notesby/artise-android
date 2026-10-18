/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import java.text.Normalizer

/** An unfinished `[[` link at the cursor: what's typed after `[[`, and where it starts. */
data class OpenWikiLink(
    /** Index of the first character after `[[`. */
    val start: Int,
    val query: String,
)

/** A note offered while typing `[[`: its name, and the text inserted for it. */
data class WikiLinkSuggestion(
    val name: String,
    val path: String,
    /** The link target: the bare name when it's unique, else the path without `.md`, as Obsidian writes it. */
    val linkText: String,
)

/** Finds an unfinished `[[link` at the cursor and the notes that fit it. */
object WikiLinkSuggestions {
    const val MAX_SUGGESTIONS = 6

    /** The `[[...` being typed at [cursor], or `null` if the cursor isn't inside one on this line. */
    fun openLinkAt(text: String, cursor: Int): OpenWikiLink? {
        if (cursor < 2 || cursor > text.length) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val before = text.substring(lineStart, cursor)
        val open = before.lastIndexOf("[[")
        if (open < 0) return null
        val query = before.substring(open + 2)
        // Closed already, or a new link started inside: not an open link.
        if (query.contains("]]") || query.contains("[[")) return null
        // "|" starts the shown text and "#" a heading: suggestions are only for the note's name.
        if (query.contains('|') || query.contains('#')) return null
        return OpenWikiLink(start = lineStart + open + 2, query = query)
    }

    /** Notes matching [query], names starting with it first, excluding [currentPath]. */
    fun suggestionsFor(query: String, notePaths: List<String>, currentPath: String?): List<WikiLinkSuggestion> {
        val wanted = query.normalized().trim()
        val names = notePaths.groupingBy { nameOf(it).normalized() }.eachCount()
        return notePaths
            .asSequence()
            .filter { it != currentPath && it.endsWith(".md") }
            .map { path -> path to nameOf(path) }
            .filter { (path, name) -> wanted.isEmpty() || name.normalized().contains(wanted) || path.normalized().contains(wanted) }
            .sortedWith(compareBy<Pair<String, String>> { !it.second.normalized().startsWith(wanted) }.thenBy { it.second.lowercase() })
            .take(MAX_SUGGESTIONS)
            .map { (path, name) ->
                val unique = names.getOrDefault(name.normalized(), 0) <= 1
                WikiLinkSuggestion(name = name, path = path, linkText = if (unique) name else path.removeSuffix(".md"))
            }
            .toList()
    }

    /** [text] with the open link at [link] completed as `[[linkText]]`, and where the cursor goes after it. */
    fun complete(text: String, link: OpenWikiLink, cursor: Int, suggestion: WikiLinkSuggestion): Pair<String, Int> {
        // Swallow a "]]" the person may already have typed after the cursor.
        val end = if (text.startsWith("]]", cursor)) cursor + 2 else cursor
        val inserted = suggestion.linkText + "]]"
        val newText = text.substring(0, link.start) + inserted + text.substring(end)
        return newText to link.start + inserted.length
    }

    private fun nameOf(path: String) = path.substringAfterLast('/').removeSuffix(".md")

    private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")
}
