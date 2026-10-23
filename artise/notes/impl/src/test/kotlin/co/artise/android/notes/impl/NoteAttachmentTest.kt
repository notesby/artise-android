/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.api.NoteAttachment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteAttachmentTest {
    @Test
    fun `the notes path is read from a file message`() {
        val json = """{"type":"m.room.message","content":{"msgtype":"m.file","body":"INV-2026-004.pdf",
            "co.artise.note":"business/documents/invoices/INV-2026-004.pdf"}}""".trimIndent()
        assertThat(NoteAttachment.read(json)).isEqualTo("business/documents/invoices/INV-2026-004.pdf")
    }

    @Test
    fun `an ordinary attachment has no notes path`() {
        assertThat(NoteAttachment.read("""{"content":{"msgtype":"m.file","body":"a.pdf"}}""")).isNull()
        assertThat(NoteAttachment.read("""{"content":{"co.artise.note":""}}""")).isNull()
        assertThat(NoteAttachment.read("""{"content":{"co.artise.note":42}}""")).isNull()
        assertThat(NoteAttachment.read(null)).isNull()
        assertThat(NoteAttachment.read("nope")).isNull()
    }

    @Test
    fun `the key is also read from a bare content`() {
        assertThat(NoteAttachment.read("""{"msgtype":"m.image","co.artise.note":"Fotos/luna.jpg"}""")).isEqualTo("Fotos/luna.jpg")
    }

    @Test
    fun `each extension opens where it should`() {
        assertThat(NoteAttachment.kindOf("Recetas/Mole.md")).isEqualTo(NoteAttachment.Kind.NOTE)
        assertThat(NoteAttachment.kindOf("notas.TXT")).isEqualTo(NoteAttachment.Kind.NOTE)
        assertThat(NoteAttachment.kindOf("docs/Recetario.pdf")).isEqualTo(NoteAttachment.Kind.PDF)
        assertThat(NoteAttachment.kindOf("Fotos/luna.jpg")).isEqualTo(NoteAttachment.Kind.IMAGE)
        assertThat(NoteAttachment.kindOf("Fotos/luna.HEIC")).isEqualTo(NoteAttachment.Kind.IMAGE)
        // Office files have no viewer or editor in the app
        assertThat(NoteAttachment.kindOf("oferta.docx")).isEqualTo(NoteAttachment.Kind.OFFICE)
        assertThat(NoteAttachment.kindOf("ventas.XLSX")).isEqualTo(NoteAttachment.Kind.OFFICE)
        assertThat(NoteAttachment.kindOf("junta.pptx")).isEqualTo(NoteAttachment.Kind.OFFICE)
        assertThat(NoteAttachment.kindOf("gastos.csv")).isEqualTo(NoteAttachment.Kind.TABLE)
        assertThat(NoteAttachment.kindOf("gastos.TSV")).isEqualTo(NoteAttachment.Kind.TABLE)
        assertThat(NoteAttachment.kindOf("charts/ventas.chart")).isEqualTo(NoteAttachment.Kind.CHART)
        assertThat(NoteAttachment.kindOf("cancion.mp3")).isEqualTo(NoteAttachment.Kind.OTHER)
        assertThat(NoteAttachment.kindOf("sinextension")).isEqualTo(NoteAttachment.Kind.OTHER)
    }

    /** The server's kind wins over the name: that's the only way to know a dashboard; unknown kinds fall back to the name. */
    @Test
    fun `the server's kind wins over the extension`() {
        assertThat(NoteAttachment.kindOf("Octubre.md", "dashboard")).isEqualTo(NoteAttachment.Kind.DASHBOARD)
        assertThat(NoteAttachment.kindOf("Octubre.md", null)).isEqualTo(NoteAttachment.Kind.NOTE)
        assertThat(NoteAttachment.kindOf("Octubre.md", "hologram")).isEqualTo(NoteAttachment.Kind.NOTE)
        assertThat(NoteAttachment.fromServer("table")).isEqualTo(NoteAttachment.Kind.TABLE)
    }
}
