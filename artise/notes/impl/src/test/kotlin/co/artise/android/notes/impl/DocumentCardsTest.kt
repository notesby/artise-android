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
import co.artise.android.notes.api.chartCardOf
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
            DocumentCard("Contratos/Contrato Ana.md", "3f2a", "Contrato Ana", DocumentCard.Change.CREATED, isDraft = true, serverKind = "note"),
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

    /** A dashboard is a `.md`: only the card's kind says so. Charts by their kind or their extension. */
    @Test
    fun `charts and dashboards take the card's kind`() {
        val json = """{"content":{"co.artise.documents":[
            {"path":"dashboards/Octubre.md","kind":"dashboard","title":"Octubre"},
            {"path":"charts/ventas.chart","kind":"chart"},
            {"path":"Notas/Plan.md","kind":"something-new"}]}}"""
            .trimIndent()
        assertThat(DocumentCards.read(json).map { it.kind })
            .containsExactly(NoteAttachment.Kind.DASHBOARD, NoteAttachment.Kind.CHART, NoteAttachment.Kind.NOTE)
            .inOrder()
    }

    /** Ari's picture of a chart (an `m.image` whose `co.artise.note` is a `.chart`) is that chart's card. */
    @Test
    fun `a chart's picture is its card`() {
        val image = """{"content":{"msgtype":"m.image","body":"Ventas por mes.png","co.artise.note":"charts/ventas.chart"}}"""
        val card = chartCardOf(image)
        assertThat(card?.path).isEqualTo("charts/ventas.chart")
        // The file's name, which the card swaps for the chart's title from the notes
        assertThat(card?.title).isEqualTo("ventas")
        assertThat(card?.kind).isEqualTo(NoteAttachment.Kind.CHART)
        // Another picture from the notes, or an ordinary one: no card
        assertThat(chartCardOf("""{"content":{"msgtype":"m.image","co.artise.note":"exports/ventas-2026-10-22.png"}}""")).isNull()
        assertThat(chartCardOf("""{"content":{"msgtype":"m.image","body":"foto.jpg"}}""")).isNull()
    }
}
