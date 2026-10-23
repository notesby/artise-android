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

    /** A file Ari posted: notes read formatted, PDFs, photos, CSVs and Office files in the viewer, anything else in its folder. */
    @Test
    fun `a file opens by its kind`() {
        assertThat(NotesEntry.initialTarget(room, "Recetas/Mole.md")).isEqualTo(NotesFlowNode.NavTarget.Note(room, "Recetas/Mole.md"))
        assertThat(NotesEntry.initialTarget(room, "notas.txt")).isEqualTo(NotesFlowNode.NavTarget.Note(room, "notas.txt"))
        assertThat(NotesEntry.initialTarget(room, "docs/Recetario.pdf")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "docs/Recetario.pdf"))
        assertThat(NotesEntry.initialTarget(room, "Fotos/luna.jpg")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "Fotos/luna.jpg"))
        assertThat(NotesEntry.initialTarget(room, "docs/oferta.docx")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "docs/oferta.docx"))
        assertThat(NotesEntry.initialTarget(room, "gastos.csv")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "gastos.csv"))
        assertThat(NotesEntry.initialTarget(room, "audio/nota.m4a")).isEqualTo(NotesFlowNode.NavTarget.Folder(room, "audio"))
        assertThat(NotesEntry.initialTarget(room, "nota.m4a")).isEqualTo(NotesFlowNode.NavTarget.Folder(room, ""))
    }

    /** A chart opens drawn; a dashboard (a `.md`) only when the card's kind says so, else as a note. */
    @Test
    fun `charts and dashboards open drawn`() {
        assertThat(NotesEntry.initialTarget(room, "charts/ventas.chart")).isEqualTo(NotesFlowNode.NavTarget.Chart(room, "charts/ventas.chart"))
        assertThat(NotesEntry.initialTarget(room, "dashboards/Octubre.md", "dashboard"))
            .isEqualTo(NotesFlowNode.NavTarget.Dashboard(room, "dashboards/Octubre.md"))
        assertThat(NotesEntry.initialTarget(room, "dashboards/Octubre.md")).isEqualTo(NotesFlowNode.NavTarget.Note(room, "dashboards/Octubre.md"))
        assertThat(NotesEntry.initialTarget(room, "gastos.tsv")).isEqualTo(NotesFlowNode.NavTarget.Document(room, "gastos.tsv"))
    }
}
