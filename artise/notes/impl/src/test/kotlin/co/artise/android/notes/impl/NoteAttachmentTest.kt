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
        assertThat(NoteAttachment.kindOf("oferta.docx")).isEqualTo(NoteAttachment.Kind.OTHER)
        assertThat(NoteAttachment.kindOf("ventas.xlsx")).isEqualTo(NoteAttachment.Kind.OTHER)
        assertThat(NoteAttachment.kindOf("sinextension")).isEqualTo(NoteAttachment.Kind.OTHER)
    }
}
