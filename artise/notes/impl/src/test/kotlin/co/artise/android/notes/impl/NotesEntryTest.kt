/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import org.junit.Test

class NotesEntryTest {
    private val room = RoomId("!abc:artise.co")

    @Test
    fun `without a chat the notes open on the chats`() {
        assertThat(NotesEntry.initialTarget(null, null)).isEqualTo(NotesFlowNode.NavTarget.Chats)
        assertThat(NotesEntry.initialTarget(null, "a.pdf")).isEqualTo(NotesFlowNode.NavTarget.Chats)
    }

    @Test
    fun `a chat opens on its folder`() {
        assertThat(NotesEntry.initialTarget(room, null)).isEqualTo(NotesFlowNode.NavTarget.Folder(room, ""))
    }

    /** A file Ari posted: notes in the editor, PDFs and photos in the viewer, anything else in its folder. */
    @Test
    fun `a file opens by its kind`() {
        assertThat(NotesEntry.initialTarget(room, "Recetas/Mole.md")).isEqualTo(NotesFlowNode.NavTarget.Editor(room, "Recetas/Mole.md", null))
        assertThat(NotesEntry.initialTarget(room, "notas.txt")).isEqualTo(NotesFlowNode.NavTarget.Editor(room, "notas.txt", null))
        assertThat(NotesEntry.initialTarget(room, "docs/Recetario.pdf")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "docs/Recetario.pdf"))
        assertThat(NotesEntry.initialTarget(room, "Fotos/luna.jpg")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "Fotos/luna.jpg"))
        assertThat(NotesEntry.initialTarget(room, "docs/oferta.docx")).isEqualTo(NotesFlowNode.NavTarget.Folder(room, "docs"))
        assertThat(NotesEntry.initialTarget(room, "oferta.docx")).isEqualTo(NotesFlowNode.NavTarget.Folder(room, ""))
    }
}
