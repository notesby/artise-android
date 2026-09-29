/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import androidx.compose.runtime.Immutable
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

/** Where a tap on a link in a note goes. */
@Immutable
sealed interface NoteLink {
    /** Another note, named as written in `[[...]]`: "Súper", "Recetas/Mole", with no `#heading` or `|shown text`. */
    data class Note(val target: String) : NoteLink

    /** A web address. */
    data class Web(val url: String) : NoteLink
}

/** The parts of an Obsidian link: `[[target#heading|shown text]]`. */
data class WikiLink(
    val target: String,
    val heading: String?,
    val shownText: String?,
) {
    /** What the reader sees: the shown text, else the note's name without its folder. */
    val label: String get() = shownText ?: target.substringAfterLast('/').removeSuffix(".md")

    companion object {
        /** Parses the text between `[[` and `]]`. */
        fun parse(inner: String): WikiLink {
            val (beforeAlias, alias) = inner.split('|', limit = 2).let { it[0] to it.getOrNull(1) }
            val (target, heading) = beforeAlias.split('#', limit = 2).let { it[0] to it.getOrNull(1) }
            return WikiLink(target.trim(), heading?.trim()?.ifEmpty { null }, alias?.trim()?.ifEmpty { null })
        }
    }
}

/**
 * Finds the file a `[[link]]` points to, the way Obsidian and the Notes API do:
 * an exact path first (with or without `.md`), else a note with that name in any folder,
 * the shortest path winning. Case and accents are ignored, since people type "super" for "Súper".
 */
object NoteLinkResolver {
    fun resolve(target: String, paths: Collection<String>): String? {
        val wanted = target.trim().removePrefix("/")
        if (wanted.isEmpty()) return null
        // "luna.jpg" names a file; "Sr. Pérez" is still a note, so only a short trailing extension counts.
        val candidates = if (EXTENSION.containsMatchIn(wanted)) listOf(wanted) else listOf("$wanted.md", wanted)
        val byKey = paths.groupBy { it.matchKey() }
        for (candidate in candidates) {
            byKey[candidate.matchKey()]?.let { return it.minBy { path -> path.length } }
        }
        // `[[Mole]]` finds `Recetas/Mole.md`: match on the file name alone.
        val nameKeys = candidates.map { it.substringAfterLast('/').matchKey() }
        return paths
            .filter { it.substringAfterLast('/').matchKey() in nameKeys }
            .minWithOrNull(compareBy<String> { it.count { c -> c == '/' } }.thenBy { it.length })
    }

    private fun String.matchKey(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val EXTENSION = Regex("\\.[A-Za-z0-9]{1,5}$")
}

/**
 * Turns `[[Note]]` and `![[photo.jpg]]` into ordinary Markdown links with the [NOTE_LINK_SCHEME] scheme,
 * so a standard Markdown parser can read notes. Code blocks and `inline code` are left untouched.
 */
object WikiLinkRewriter {
    const val NOTE_LINK_SCHEME = "artise-note:"

    private val WIKI_LINK = Regex("!?\\[\\[([^\\[\\]\\n]+)]]")
    private val FENCE = Regex("^\\s{0,3}(```|~~~)")

    fun rewrite(markdown: String): String {
        var inFence = false
        return markdown.lines().joinToString("\n") { line ->
            when {
                FENCE.containsMatchIn(line) -> {
                    inFence = !inFence
                    line
                }
                inFence -> line
                else -> rewriteOutsideInlineCode(line)
            }
        }
    }

    /** Link destination for [target], decoded again with [targetOf]. */
    fun destination(target: String): String = NOTE_LINK_SCHEME + URLEncoder.encode(target, Charsets.UTF_8.name()).replace("+", "%20")

    /** The note target of a destination made by [destination], or `null` for any other link. */
    fun targetOf(destination: String): String? =
        destination.takeIf { it.startsWith(NOTE_LINK_SCHEME) }?.let { URLDecoder.decode(it.removePrefix(NOTE_LINK_SCHEME), Charsets.UTF_8.name()) }

    // Backticks split a line into text (even parts) and code spans (odd parts); only text gets rewritten.
    private fun rewriteOutsideInlineCode(line: String): String = line.split('`').mapIndexed { index, part ->
        if (index % 2 == 1) part else WIKI_LINK.replace(part) { match -> toMarkdownLink(WikiLink.parse(match.groupValues[1])) }
    }.joinToString("`")

    private fun toMarkdownLink(link: WikiLink): String {
        val label = link.label.replace("[", "\\[").replace("]", "\\]")
        return "[$label](<${destination(link.target)}>)"
    }
}
