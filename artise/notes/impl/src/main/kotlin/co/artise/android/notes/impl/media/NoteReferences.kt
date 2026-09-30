/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.media

import co.artise.android.notes.impl.markdown.NoteLinkResolver
import co.artise.android.notes.impl.ui.note.NoteEmbedRemover
import java.net.URLDecoder

/**
 * Which photos and files each note uses: shown (`![[luna.jpg]]`, `![foto](luna.jpg)`) or linked (`[[factura.pdf]]`,
 * `[factura](factura.pdf)`). A name finds the file the same way the note view does, so what counts as "used" here is
 * what people see.
 */
object NoteReferences {
    // Both "![[x]]" and "[[x]]"; the part after | or # is the shown text or a heading.
    private val WIKI = Regex("!?\\[\\[([^\\[\\]|#\\n]+)((?:[|#][^\\]\\n]*)?)]]")

    // Both "![alt](x)" and "[text](x)".
    private val MARKDOWN = Regex("(!?)\\[([^\\]\\n]*)]\\(<?([^)>\\s]+)>?\\)")

    /** The files [markdown] uses, among [paths] (notes themselves don't count). */
    fun filesUsed(markdown: String, paths: Collection<String>): Set<String> {
        val targets = WIKI.findAll(markdown).map { it.groupValues[1].trim() } +
            MARKDOWN.findAll(markdown).map { it.groupValues[3] }.filter { "://" !in it && !it.startsWith("mailto:") }.map(::decode)
        return targets
            .mapNotNull { NoteLinkResolver.resolve(it, paths) }
            .filterNot { it.endsWith(".md") }
            .toSet()
    }

    /** For each note given as (path, text), the files it uses: file path → the notes using it, in order. */
    fun index(notes: List<Pair<String, String>>, paths: Collection<String>): Map<String, List<String>> =
        notes.flatMap { (note, text) -> filesUsed(text, paths).map { it to note } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, users) -> users.distinct().sorted() }

    /**
     * [markdown] without any use of [path]: shown copies go (with their line when alone on it), links keep the words
     * people read. [paths] finds what each name means, so a same-named file elsewhere is left alone.
     */
    fun remove(markdown: String, path: String, paths: Collection<String>): String {
        fun isIt(target: String) = NoteLinkResolver.resolve(target, paths) == path
        // Shown copies first, reusing the embed remover (it tidies the empty line left behind).
        var text = markdown
        WIKI.findAll(markdown).filter { it.value.startsWith("!") && isIt(it.groupValues[1].trim()) }.map { it.groupValues[1].trim() }.toSet()
            .forEach { name -> text = removeEmbedsNamed(text, name) }
        text = MARKDOWN.replace(text) { match ->
            val (bang, words, target) = match.destructured
            when {
                !isIt(decode(target)) -> match.value
                bang == "!" -> ""
                else -> words
            }
        }
        text = WIKI.replace(text) { match ->
            val name = match.groupValues[1].trim()
            when {
                match.value.startsWith("!") || !isIt(name) -> match.value
                // [[factura.pdf|la factura]] → "la factura"; [[factura.pdf]] → "factura.pdf".
                match.groupValues[2].startsWith("|") -> match.groupValues[2].removePrefix("|")
                else -> name.substringAfterLast('/')
            }
        }
        return text
    }

    private fun removeEmbedsNamed(text: String, name: String): String = NoteEmbedRemover.remove(text, name)

    private fun decode(target: String) = URLDecoder.decode(target, Charsets.UTF_8.name()).removePrefix("./")
}
