/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.common

import co.artise.android.notes.impl.ui.choices.NotesChoicesPresenter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteNamesTest {
    /** A typed name becomes a Markdown file in the current folder, trimmed, without doubling ".md". */
    @Test
    fun `names become paths`() {
        assertThat(NoteNames.pathFor("", " Súper ")).isEqualTo("Súper.md")
        assertThat(NoteNames.pathFor("Recetas", "Mole.md")).isEqualTo("Recetas/Mole.md")
    }

    /** Empty names, slashes and hidden names are refused; a name already used (in any case) is reported as taken. */
    @Test
    fun `bad and taken names are refused`() {
        val existing = listOf("Recetas/Mole.md")
        assertThat(NoteNames.problemWith("Recetas", "  ", existing)).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.problemWith("Recetas", "a/b", existing)).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.problemWith("Recetas", ".secreto", existing)).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.problemWith("Recetas", "mole", existing)).isEqualTo(NoteNameProblem.EXISTS)
        assertThat(NoteNames.problemWith("", "Mole", existing)).isNull()
    }

    /** "Keep both" picks the first free "(n)" name in the same folder. */
    @Test
    fun `free names for keeping both`() {
        val taken = setOf("Recetas/Mole.md", "Recetas/Mole (2).md")
        assertThat(NotesChoicesPresenter.freeName("Recetas/Mole.md", taken)).isEqualTo("Recetas/Mole (3).md")
        assertThat(NotesChoicesPresenter.freeName("Súper.md", emptySet())).isEqualTo("Súper (2).md")
    }
}
