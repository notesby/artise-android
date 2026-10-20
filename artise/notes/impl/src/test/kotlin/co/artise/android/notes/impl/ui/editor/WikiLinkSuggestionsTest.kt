/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import co.artise.android.notes.api.NotesPage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WikiLinkSuggestionsTest {
    private val paths = listOf("Súper.md", "Recetas/Mole.md", "Viejo/Mole.md", "Recetas/Sopa.md", "Fotos/luna.jpg")

    /** Typing "[[mo" at the end of a line opens a link with query "mo". */
    @Test
    fun `open link is found at the cursor`() {
        val text = "Ver [[mo"
        assertThat(WikiLinkSuggestions.openLinkAt(text, text.length)).isEqualTo(OpenWikiLink(start = 6, query = "mo"))
    }

    /** A finished link, a heading or shown text, or a link on an earlier line doesn't trigger suggestions. */
    @Test
    fun `closed links and other lines are ignored`() {
        assertThat(WikiLinkSuggestions.openLinkAt("[[Súper]] y", 11)).isNull()
        assertThat(WikiLinkSuggestions.openLinkAt("[[Súper#Lác", 11)).isNull()
        assertThat(WikiLinkSuggestions.openLinkAt("[[Súper|la", 10)).isNull()
        assertThat(WikiLinkSuggestions.openLinkAt("[[Sú\nhola", 9)).isNull()
    }

    /** Suggestions ignore accents, put names starting with the query first, and skip photos and the note itself. */
    @Test
    fun `suggestions match names`() {
        val names = WikiLinkSuggestions.suggestionsFor("s", paths, currentPath = "Recetas/Sopa.md").map { it.name }
        assertThat(names).containsExactly("Súper", "Mole").inOrder()
        assertThat(WikiLinkSuggestions.suggestionsFor("super", paths, null).single().path).isEqualTo("Súper.md")
        assertThat(WikiLinkSuggestions.suggestionsFor("luna", paths, null)).isEmpty()
    }

    /** A name used in two folders is linked by path, showing the name, so the link points at the right note. */
    @Test
    fun `duplicate names link by path`() {
        val moles = WikiLinkSuggestions.suggestionsFor("mole", paths, null)
        assertThat(moles.map { it.linkText }).containsExactly("Recetas/Mole|Mole", "Viejo/Mole|Mole")
        assertThat(WikiLinkSuggestions.suggestionsFor("sopa", paths, null).single().linkText).isEqualTo("Sopa")
    }

    /** Choosing a suggestion completes the link and puts the cursor after "]]", swallowing a "]]" already typed. */
    @Test
    fun `completing inserts the link`() {
        val suggestion = WikiLinkSuggestion("Súper", "Súper.md", "Súper")
        val text = "Ver [[sú y más"
        val link = WikiLinkSuggestions.openLinkAt(text, 8)!!
        assertThat(WikiLinkSuggestions.complete(text, link, 8, suggestion)).isEqualTo("Ver [[Súper]] y más" to 13)
        val typed = "Ver [[sú]]"
        assertThat(WikiLinkSuggestions.complete(typed, WikiLinkSuggestions.openLinkAt(typed, 8)!!, 8, suggestion)).isEqualTo("Ver [[Súper]]" to 13)
    }

    private val pages = listOf(
        NotesPage("people/Ana.md", "Ana", "person", "people", listOf("Mamá"), "Mamá de Hector", isNote = true),
        NotesPage("clients/Ana.md", "Ana", "client", "clients", emptyList(), "Dueña de la panadería", isNote = true),
        NotesPage("Fotos/playa.jpg", "playa.jpg", "image", "", emptyList(), "", isNote = false),
    )
    private val pagePaths = listOf("people/Ana.md", "clients/Ana.md", "Súper.md")

    /** Typing an alias offers the page, and the link is written `[[Title|Alias]]`, as the server reads it; by path when the title is shared. */
    @Test
    fun `aliases suggest the page and keep the typed name`() {
        val suggestion = WikiLinkSuggestions.suggestionsFor("mam", listOf("people/Ana.md", "Súper.md"), null, pages.take(1)).single()
        assertThat(suggestion.name).isEqualTo("Ana")
        assertThat(suggestion.alias).isEqualTo("Mamá")
        assertThat(suggestion.linkText).isEqualTo("Ana|Mamá")
        assertThat(WikiLinkSuggestions.suggestionsFor("mam", pagePaths, null, pages).single().linkText).isEqualTo("people/Ana|Mamá")
    }

    /** Two pages share the title "Ana": both are offered with their folder and summary, and linked by path. */
    @Test
    fun `shared titles show both and link by path`() {
        val anas = WikiLinkSuggestions.suggestionsFor("ana", pagePaths, null, pages)
        assertThat(anas.map { it.linkText }).containsExactly("clients/Ana|Ana", "people/Ana|Ana")
        assertThat(anas.all { it.sharesName }).isTrue()
        assertThat(anas.map { it.folder to it.summary }).containsExactly("clients" to "Dueña de la panadería", "people" to "Mamá de Hector")
    }

    /** A photo or document the server lists as a page can be linked too, by its file name. */
    @Test
    fun `file pages are suggested by name`() {
        val playa = WikiLinkSuggestions.suggestionsFor("playa", pagePaths, null, pages).single()
        assertThat(playa.linkText).isEqualTo("playa.jpg")
        assertThat(playa.type).isEqualTo("image")
    }
}
