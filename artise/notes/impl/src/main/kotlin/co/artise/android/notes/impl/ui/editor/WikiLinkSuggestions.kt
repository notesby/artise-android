/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import co.artise.android.notes.api.NotesPage
import java.text.Normalizer

/** An unfinished `[[` link at the cursor: what's typed after `[[`, and where it starts. */
data class OpenWikiLink(
    /** Index of the first character after `[[`. */
    val start: Int,
    val query: String,
)

/** A note or page offered while typing `[[`: what to show, and the text inserted for it. */
data class WikiLinkSuggestion(
    /** The page's title, or the note's file name. */
    val name: String,
    val path: String,
    /**
     * Everything between `[[` and `]]`, as Obsidian writes it: the bare name when it's unique (`Ana`), the path
     * when two share it (`people/Ana|Ana`), and the alias typed as shown text (`Ana|Mamá`).
     */
    val linkText: String,
    /** The alias the query matched, when it wasn't the name itself. */
    val alias: String? = null,
    /** The page's folder, shown when two pages share a name so they can be told apart. */
    val folder: String = "",
    /** One line about who or what it is; empty for plain notes. */
    val summary: String = "",
    /** `person`, `place`, `image`...; empty for plain notes. */
    val type: String = "",
    /** Another note or page has the same name, so the link is written by path and the folder is shown. */
    val sharesName: Boolean = false,
)

/** Finds an unfinished `[[link` at the cursor and the notes and pages that fit it. */
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

    /**
     * Notes and [pages] matching [query] by name, title or alias, names starting with it first, excluding [currentPath].
     * A page found through an alias is written `[[Title|Alias]]`; a name two entries share is written by path.
     */
    fun suggestionsFor(query: String, notePaths: List<String>, currentPath: String?, pages: List<NotesPage> = emptyList()): List<WikiLinkSuggestion> {
        val wanted = query.normalized().trim()
        val byPath = pages.associateBy { it.path }
        val entries = notePaths.filter { it.endsWith(".md") }.map { path -> byPath[path]?.let(::Entry) ?: Entry(path) } +
            pages.filter { it.path !in notePaths.toSet() }.map(::Entry)
        val names = entries.groupingBy { it.name.normalized() }.eachCount()
        return entries
            .asSequence()
            .filter { it.path != currentPath }
            .mapNotNull { entry ->
                val nameMatches = wanted.isEmpty() || entry.name.normalized().contains(wanted) || entry.path.normalized().contains(wanted)
                val alias = if (nameMatches) null else entry.aliases.firstOrNull { it.normalized().contains(wanted) }
                if (nameMatches || alias != null) Match(entry, alias) else null
            }
            .sortedWith(
                compareBy<Match> { !(it.alias ?: it.entry.name).normalized().startsWith(wanted) }
                    .thenBy { it.entry.isFile }
                    .thenBy { (it.alias ?: it.entry.name).lowercase() },
            )
            .take(MAX_SUGGESTIONS)
            .map { (entry, alias) ->
                val unique = names.getOrDefault(entry.name.normalized(), 0) <= 1
                val target = if (unique) entry.name else entry.path.removeSuffix(".md")
                val shown = alias ?: entry.name.takeIf { !unique }
                WikiLinkSuggestion(
                    name = entry.name,
                    path = entry.path,
                    linkText = if (shown != null) "$target|$shown" else target,
                    alias = alias,
                    folder = entry.folder,
                    summary = entry.summary,
                    type = entry.type,
                    sharesName = !unique,
                )
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

    /** A note or page that can be suggested: a plain note has its file name and nothing else. */
    private data class Entry(
        val path: String,
        val name: String,
        val aliases: List<String>,
        val folder: String,
        val summary: String,
        val type: String,
        val isFile: Boolean,
    ) {
        constructor(path: String) : this(path, nameOf(path), emptyList(), path.substringBeforeLast('/', ""), "", "", isFile = false)

        constructor(page: NotesPage) : this(
            path = page.path,
            name = if (page.isNote) page.title else page.path.substringAfterLast('/'),
            aliases = page.aliases,
            folder = page.folder.ifEmpty { page.path.substringBeforeLast('/', "") },
            summary = page.summary,
            type = page.type,
            isFile = !page.isNote,
        )
    }

    private data class Match(val entry: Entry, val alias: String?)

    private fun nameOf(path: String) = path.substringAfterLast('/').removeSuffix(".md")

    private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")
}
