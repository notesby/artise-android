/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteReferencesTest {
    private val paths = listOf("Recetas/Mole.md", "attachments/luna.jpg", "attachments/factura.pdf", "Viajes/2026/luna.jpg", "attachments/sol.png")

    /** Shown photos, wiki links and Markdown images and links all count; web links and notes don't. */
    @Test
    fun `what counts as using a file`() {
        val note = """
            ![[luna.jpg]]
            Ver [[factura.pdf|la factura]] y [[Mole]].
            ![](attachments/sol.png) [web](https://artise.co/x.png)
        """.trimIndent()
        assertThat(NoteReferences.filesUsed(note, paths)).containsExactly("attachments/luna.jpg", "attachments/factura.pdf", "attachments/sol.png")
    }

    /** The index says which notes use each file, each note once. */
    @Test
    fun `index of uses`() {
        val index = NoteReferences.index(
            listOf("B.md" to "![[luna.jpg]] ![[luna.jpg]]", "A.md" to "[[luna.jpg]]", "C.md" to "nada"),
            paths,
        )
        assertThat(index).containsExactly("attachments/luna.jpg", listOf("A.md", "B.md"))
    }

    /** Removing: a shown photo goes with its line, a link keeps its words, other files stay. */
    @Test
    fun `removing a file from a note`() {
        val note = "Hoy\n\n![[luna.jpg]]\n\nVer [[luna.jpg|la foto]] y [luna](attachments/luna.jpg).\n![[factura.pdf]]"
        assertThat(NoteReferences.remove(note, "attachments/luna.jpg", paths))
            .isEqualTo("Hoy\n\nVer la foto y luna.\n![[factura.pdf]]")
        // A name that means another file here (the path is written out) is left alone.
        assertThat(NoteReferences.remove("![[Viajes/2026/luna.jpg]]", "attachments/luna.jpg", paths)).isEqualTo("![[Viajes/2026/luna.jpg]]")
    }
}
