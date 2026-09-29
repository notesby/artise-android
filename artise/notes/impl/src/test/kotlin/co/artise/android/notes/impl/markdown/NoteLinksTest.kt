/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteLinksTest {
    private val paths = listOf("Súper.md", "Recetas/Mole.md", "Viejo/Recetas/Mole.md", "Fotos/luna.jpg", "Listas/Súper.md")

    /** `[[target#heading|shown text]]` splits into its three parts; the label falls back to the note's name. */
    @Test
    fun `wiki link parts are parsed`() {
        assertThat(WikiLink.parse("Recetas/Mole#Salsa|el mole")).isEqualTo(WikiLink("Recetas/Mole", "Salsa", "el mole"))
        assertThat(WikiLink.parse("Recetas/Mole").label).isEqualTo("Mole")
    }

    /** A bare name finds the note in any folder, preferring the shallowest one, as Obsidian does. */
    @Test
    fun `bare name finds the note in any folder`() {
        assertThat(NoteLinkResolver.resolve("Mole", paths)).isEqualTo("Recetas/Mole.md")
    }

    /** An exact path wins over a same-named note elsewhere. */
    @Test
    fun `exact path wins`() {
        assertThat(NoteLinkResolver.resolve("Listas/Súper", paths)).isEqualTo("Listas/Súper.md")
        assertThat(NoteLinkResolver.resolve("Súper", paths)).isEqualTo("Súper.md")
    }

    /** People type without accents or capitals; "super" still finds "Súper.md". */
    @Test
    fun `case and accents are ignored`() {
        assertThat(NoteLinkResolver.resolve("super", paths)).isEqualTo("Súper.md")
    }

    /** Links to files keep their extension: `![[luna.jpg]]` finds the photo. */
    @Test
    fun `files with an extension resolve`() {
        assertThat(NoteLinkResolver.resolve("luna.jpg", paths)).isEqualTo("Fotos/luna.jpg")
    }

    /** A dot inside a name ("Sr. Pérez") doesn't make it a file: the note is still found. */
    @Test
    fun `dots in names still find notes`() {
        assertThat(NoteLinkResolver.resolve("Sr. Pérez", listOf("Contactos/Sr. Pérez.md"))).isEqualTo("Contactos/Sr. Pérez.md")
    }

    /** A link to a note that doesn't exist yet resolves to nothing, so the app can offer to create it. */
    @Test
    fun `missing note resolves to null`() {
        assertThat(NoteLinkResolver.resolve("Leche", paths)).isNull()
    }

    /** Wiki links become Markdown links the parser understands, with the target recoverable from the destination. */
    @Test
    fun `wiki links are rewritten to markdown links`() {
        val rewritten = WikiLinkRewriter.rewrite("Ver [[Recetas/Mole#Salsa|el mole]] y [[Súper]].")
        assertThat(rewritten).isEqualTo(
            "Ver [el mole](<${WikiLinkRewriter.destination("Recetas/Mole")}>) y [Súper](<${WikiLinkRewriter.destination("Súper")}>)."
        )
        assertThat(WikiLinkRewriter.targetOf(WikiLinkRewriter.destination("Mis notas/Súper"))).isEqualTo("Mis notas/Súper")
    }

    /** Code shows links literally: nothing inside `inline code` or fenced blocks is rewritten. */
    @Test
    fun `code is left alone`() {
        val markdown = "Escribe `[[Nota]]` así.\n```\n[[Nota]]\n```\n[[Nota]]"
        val lines = WikiLinkRewriter.rewrite(markdown).lines()
        assertThat(lines[0]).isEqualTo("Escribe `[[Nota]]` así.")
        assertThat(lines[2]).isEqualTo("[[Nota]]")
        assertThat(lines[4]).startsWith("[Nota](<")
    }

    /** Ordinary web links are not note links. */
    @Test
    fun `web links are not note targets`() {
        assertThat(WikiLinkRewriter.targetOf("https://artise.co")).isNull()
    }
}
