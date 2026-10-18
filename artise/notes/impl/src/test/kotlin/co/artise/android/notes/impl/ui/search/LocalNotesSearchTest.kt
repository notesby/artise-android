/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import co.artise.android.notes.api.LocalFile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalNotesSearchTest {
    private fun note(path: String, content: String) = LocalFile(path, "v", 1, 1, isNote = true, content = content, hasLocalEdits = false)

    private val files = listOf(
        note("Recetas/Mole.md", "# Mole\n- chocolate amargo\n- chiles"),
        note("Súper.md", "- leche\n- Chocolate en polvo"),
        note("Café.md", "Comprar granos"),
        LocalFile("Fotos/chocolate.jpg", "v", 1, 1, isNote = false, content = null, hasLocalEdits = false),
    )

    /** Lines are matched ignoring case, and each hit shows the matching lines. */
    @Test
    fun `matching lines are returned`() {
        val hits = LocalNotesSearch.search(files, "chocolate")
        assertThat(hits.map { it.path }).containsExactly("Recetas/Mole.md", "Súper.md").inOrder()
        assertThat(hits.first().lines).containsExactly("- chocolate amargo")
    }

    /** "cafe" finds "Café": accents don't matter, as people type fast on phones. */
    @Test
    fun `accents are ignored`() {
        assertThat(LocalNotesSearch.search(files, "cafe").map { it.path }).containsExactly("Café.md")
    }

    /** Notes whose name matches come first. */
    @Test
    fun `name matches come first`() {
        val hits = LocalNotesSearch.search(files + note("Chocolate.md", "postre"), "chocolate")
        assertThat(hits.first().path).isEqualTo("Chocolate.md")
    }

    /** Every word must appear, in any order. */
    @Test
    fun `all words must match`() {
        assertThat(LocalNotesSearch.search(files, "polvo chocolate").map { it.path }).containsExactly("Súper.md")
    }

    /** Photos and documents aren't searched: their text isn't on the phone. Blank queries find nothing. */
    @Test
    fun `raw files and blank queries are skipped`() {
        assertThat(LocalNotesSearch.search(files, "chocolate").map { it.path }).doesNotContain("Fotos/chocolate.jpg")
        assertThat(LocalNotesSearch.search(files, "   ")).isEmpty()
    }
}
