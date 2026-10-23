/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.api.DocumentCard
import co.artise.android.notes.api.DocumentCards
import co.artise.android.notes.api.NoteAttachment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentCardsTest {
    /** A reply about a new draft: one card with its version, title, change and draft mark; the kind follows the path. */
    @Test
    fun `a reply's documents are read`() {
        val json = """{"content":{"msgtype":"m.text","body":"Listo","co.artise.documents":[
            {"path":"Contratos/Contrato Ana.md","version":"3f2a","kind":"note","title":"Contrato Ana","change":"created","draft":true},
            {"path":"ventas.xlsx","change":"changed"}]}}"""
            .trimIndent()
        val cards = DocumentCards.read(json)
        assertThat(cards).containsExactly(
            DocumentCard("Contratos/Contrato Ana.md", "3f2a", "Contrato Ana", DocumentCard.Change.CREATED, isDraft = true),
            DocumentCard("ventas.xlsx", null, "ventas.xlsx", DocumentCard.Change.CHANGED, isDraft = false),
        ).inOrder()
        assertThat(cards.map { it.kind }).containsExactly(NoteAttachment.Kind.NOTE, NoteAttachment.Kind.OFFICE).inOrder()
    }

    /** Ari edits its progress message into the reply: the edit's documents win over the original's. */
    @Test
    fun `the latest edit's documents come first`() {
        val original = """{"content":{"msgtype":"m.notice","body":"Trabajando…"}}"""
        val edit = """{"content":{"msgtype":"m.notice","body":"* Listo","m.new_content":{"msgtype":"m.notice","body":"Listo",
            "co.artise.documents":[{"path":"Notas/Plan.md","change":"changed"}]}}}"""
            .trimIndent()
        assertThat(DocumentCards.read(edit, original).map { it.path }).containsExactly("Notas/Plan.md")
        // No edit: the original's.
        val withCards = """{"content":{"co.artise.documents":[{"path":"a.pdf"}]}}"""
        assertThat(DocumentCards.read(null, withCards).map { it.path }).containsExactly("a.pdf")
    }

    /** Entries without a path are skipped, repeats collapse, and at most four show; junk gives no cards. */
    @Test
    fun `odd lists are cleaned up`() {
        val json = """{"content":{"co.artise.documents":[{"title":"no path"},{"path":"/a.md/"},{"path":"a.md"},
            {"path":"b.md"},{"path":"c.md"},{"path":"d.md"},{"path":"e.md"},"text",{"path":"f.md","draft":"yes"}]}}"""
            .trimIndent()
        assertThat(DocumentCards.read(json).map { it.path }).containsExactly("a.md", "b.md", "c.md", "d.md").inOrder()
        assertThat(DocumentCards.read("""{"content":{"co.artise.documents":"a.md"}}""")).isEmpty()
        assertThat(DocumentCards.read("""{"content":{"body":"hola"}}""")).isEmpty()
        assertThat(DocumentCards.read("co.artise.documents nope")).isEmpty()
        assertThat(DocumentCards.read(null)).isEmpty()
    }
}
