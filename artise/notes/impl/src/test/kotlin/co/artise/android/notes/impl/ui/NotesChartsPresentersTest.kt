/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.ChartSpec
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.share.ChatMessenger
import co.artise.android.notes.impl.share.DocumentShareEvent
import co.artise.android.notes.impl.share.DocumentSharePresenter
import co.artise.android.notes.impl.share.MatrixChatMessenger
import co.artise.android.notes.impl.share.Mention
import co.artise.android.notes.impl.share.ShareProblem
import co.artise.android.notes.impl.share.withAssistant
import co.artise.android.notes.impl.ui.chart.NotesChartEvent
import co.artise.android.notes.impl.ui.chart.NotesChartPresenter
import co.artise.android.notes.impl.ui.chart.localDay
import co.artise.android.notes.impl.ui.dashboard.DashboardHtml
import co.artise.android.notes.impl.ui.dashboard.DashboardProblem
import co.artise.android.notes.impl.ui.dashboard.NotesDashboardPresenter
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.folder.NotesFolderEntry
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class FakeChatMessenger(var canAsk: Boolean = true) : ChatMessenger {
    val sent = mutableListOf<Triple<RoomId, String, Mention?>>()
    var sendResult: Result<Unit> = Result.success(Unit)

    override suspend fun canAsk(roomId: RoomId): Boolean = canAsk

    override suspend fun send(roomId: RoomId, text: String, mention: Mention?): Result<Unit> {
        sent += Triple(roomId, text, mention)
        return sendResult
    }
}

class NotesChartsPresentersTest {
    private val room = RoomId("!abc:artise.co")
    private val messenger = FakeChatMessenger()

    private fun shareFactory(repository: FakeNotesRepository) =
        DocumentSharePresenter.Factory { roomId, path -> DocumentSharePresenter(roomId, path, repository, messenger) }

    private fun aChart(title: String = "Ventas por mes", caption: String = "Sin IVA") = NotesChart(
        path = "charts/ventas.chart",
        version = "c1",
        spec = ChartSpec(title, "bar", listOf("Mes", "Ventas"), listOf(listOf("2026-05", "12000")), listOf("Ventas"), "MXN", caption, emptyList(), ""),
        asOf = "2026-10-22",
        svg = "<svg/>",
    )

    private fun aDrawnFile(path: String, kind: String, readOnly: Boolean = false, title: String? = null) =
        LocalFile(path, "v1", 10, 1, isNote = true, content = "x", hasLocalEdits = false, serverKind = kind, title = title, isReadOnly = readOnly)

    /** The chart screen shows the server's 1024 px picture and asks for the chart's data; pull to refresh syncs first. */
    @Test
    fun `chart screen loads the picture and the chart`() = runTest {
        val picture = Files.createTempFile("chart", ".png").toFile()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aDrawnFile("charts/ventas.chart", "chart"))))
        repository.chartResult = { Result.success(aChart()) }
        repository.previewResult = { NotesPreview.Image(picture, 1024, 576) }
        NotesChartPresenter(room, "charts/ventas.chart", repository, shareFactory(repository)).test {
            val state = consumeItemsUntilPredicate { it.chart != null && it.picture != null }.last()
            assertThat(state.title).isEqualTo("Ventas por mes")
            assertThat(state.altText).isEqualTo("Ventas por mes. Sin IVA")
            assertThat(state.canEdit).isTrue()
            assertThat(state.failed).isFalse()
            assertThat(repository.previewRequests).containsExactly("charts/ventas.chart" to 1024)
            assertThat(repository.chartRequests).containsExactly("charts/ventas.chart" to false)
            val syncsBefore = repository.syncCount
            state.eventSink(NotesChartEvent.Refresh)
            consumeItemsUntilPredicate { !it.isRefreshing && repository.chartRequests.size == 2 }
            assertThat(repository.syncCount).isEqualTo(syncsBefore + 1)
            assertThat(repository.previewRequests).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A chart that can't be loaded says "couldn't load the chart" under where its picture would be. */
    @Test
    fun `a chart that can't load says so`() = runTest {
        val repository = FakeNotesRepository()
        repository.chartResult = { Result.failure(NotesException.BadChart("row 2, Ventas: 'mucho' isn't a number")) }
        NotesChartPresenter(room, "charts/ventas.chart", repository, shareFactory(repository)).test {
            val state = consumeItemsUntilPredicate { it.failed }.last()
            assertThat(state.title).isEqualTo("ventas")
            assertThat(state.chart).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `days show in the person's format`() {
        assertThat(localDay("2026-10-22", java.util.Locale.forLanguageTag("es-MX"))).isEqualTo("22 de octubre de 2026")
        assertThat(localDay("2026-10-22", java.util.Locale.US)).isEqualTo("October 22, 2026")
        assertThat(localDay("ayer", java.util.Locale.US)).isEqualTo("ayer")
    }

    /** Opening the screen never makes an export (it's drawn on the server each time); tapping Share makes exactly one. */
    @Test
    fun `share exports only when tapped`() = runTest {
        val repository = FakeNotesRepository()
        repository.chartResult = { Result.success(aChart()) }
        NotesChartPresenter(room, "charts/ventas.chart", repository, shareFactory(repository)).test {
            val state = consumeItemsUntilPredicate { it.chart != null && it.share.canAskLink }.last()
            assertThat(repository.exportRequests).isEmpty()
            state.share.eventSink(DocumentShareEvent.Export(ExportFormat.PNG, state.title))
            val ready = consumeItemsUntilPredicate { it.share.ready != null }.last()
            assertThat(ready.share.ready?.name).isEqualTo("Ventas por mes.png")
            assertThat(ready.share.ready?.mimeType).isEqualTo("image/png")
            assertThat(repository.exportRequests).containsExactly(Triple("charts/ventas.chart", ExportFormat.PNG, "Ventas por mes"))
            ready.share.eventSink(DocumentShareEvent.ExportHandled)
            consumeItemsUntilPredicate { it.share.ready == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed export says so`() = runTest {
        val repository = FakeNotesRepository()
        repository.exportResult = { _, _, _ -> Result.failure(NotesException.Server(503, "unavailable")) }
        DocumentSharePresenter(room, "Informe.md", repository, messenger).test {
            val state = consumeItemsUntilPredicate { it.canAskLink }.last()
            state.eventSink(DocumentShareEvent.Export(ExportFormat.PDF, "Informe"))
            val failed = consumeItemsUntilPredicate { it.problem != null }.last()
            assertThat(failed.problem).isEqualTo(ShareProblem.EXPORT_FAILED)
            assertThat(failed.exporting).isNull()
            failed.eventSink(DocumentShareEvent.DismissDialog)
            consumeItemsUntilPredicate { it.problem == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "Ask Ari for a link" posts the sentence (in the app's language, built by the view) in the document's chat, mentioning Ari. */
    @Test
    fun `asking Ari for a link posts in the document's chat`() = runTest {
        DocumentSharePresenter(room, "charts/ventas.chart", FakeNotesRepository(), messenger).test {
            val state = consumeItemsUntilPredicate { it.canAskLink }.last()
            val ari = Mention("@ari:artise.co", "Ari")
            state.eventSink(DocumentShareEvent.AskForLink("Ari, comparte «Ventas por mes» con un enlace", ari))
            assertThat(consumeItemsUntilPredicate { it.linkAsked }.last().problem).isNull()
            assertThat(messenger.sent).containsExactly(Triple(room, "Ari, comparte «Ventas por mes» con un enlace", ari))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Ari's name becomes a mention in the message's HTML; the rest is escaped; the assistant's own name replaces "Ari". */
    @Test
    fun `the link request mentions Ari`() {
        assertThat(MatrixChatMessenger.mentionHtml("Ari, share “a<b” with a link", "Ari", "@ari:artise.co"))
            .isEqualTo("<a href=\"https://matrix.to/#/@ari:artise.co\">Ari</a>, share “a&lt;b” with a link")
        assertThat(withAssistant("Ari, share “Ari's plan” with a link", "Luna")).isEqualTo("Luna, share “Luna's plan” with a link")
        assertThat(withAssistant("Pedir un enlace a Ari", "Luna")).isEqualTo("Pedir un enlace a Luna")
    }

    /** A dashboard loads its page; a business report (read-only) offers Share but not Edit. */
    @Test
    fun `dashboard loads its page and a report can't be edited`() = runTest {
        val repository = FakeNotesRepository(
            files = mutableMapOf(room to listOf(aDrawnFile("business/reports/2026-10.md", "dashboard", readOnly = true, title = "Octubre 2026"))),
        )
        repository.dashboardResult = { Result.success("<html><head></head><body>Hola</body></html>") }
        NotesDashboardPresenter(room, "business/reports/2026-10.md", repository, shareFactory(repository)).test {
            val state = consumeItemsUntilPredicate { it.html != null && it.title == "Octubre 2026" }.last()
            assertThat(state.isReadOnly).isTrue()
            assertThat(state.problem).isNull()
            assertThat(repository.exportRequests).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note that isn't a dashboard is said so`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aDrawnFile("Octubre.md", "dashboard"))))
        NotesDashboardPresenter(room, "Octubre.md", repository, shareFactory(repository)).test {
            val state = consumeItemsUntilPredicate { !it.isLoading && it.problem != null }.last()
            assertThat(state.problem).isEqualTo(DashboardProblem.NOT_A_DASHBOARD)
            assertThat(state.isReadOnly).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The page's content policy goes back in as a meta tag, first thing in its head. */
    @Test
    fun `dashboard pages keep their content policy`() {
        assertThat(DashboardHtml.withPolicy("<html><head><title>x</title></head></html>"))
            .isEqualTo("<html><head><meta http-equiv=\"Content-Security-Policy\" content=\"${DashboardHtml.POLICY}\"><title>x</title></head></html>")
        assertThat(DashboardHtml.withPolicy("<p>sin head</p>")).startsWith("<meta http-equiv=\"Content-Security-Policy\"")
    }

    /** A `.md` the tree says is a dashboard lists as one, by its title; a chart likewise, never by its YAML. */
    @Test
    fun `charts and dashboards list by title with their kind`() {
        val entries = NotesFolderEntries.of(
            listOf(
                aDrawnFile("Octubre.md", "dashboard", title = "Octubre 2026"),
                aDrawnFile("ventas.chart", "chart"),
                LocalFile("Mole.md", "m1", 10, 1, isNote = true, content = "# Mole", hasLocalEdits = false),
            ),
            folder = "",
        ).filterIsInstance<NotesFolderEntry.Note>().associateBy { it.path }
        assertThat(entries["Octubre.md"]?.kind).isEqualTo(NoteAttachment.Kind.DASHBOARD)
        assertThat(entries["Octubre.md"]?.name).isEqualTo("Octubre 2026")
        assertThat(entries["ventas.chart"]?.kind).isEqualTo(NoteAttachment.Kind.CHART)
        assertThat(entries["ventas.chart"]?.name).isEqualTo("ventas")
        assertThat(entries["ventas.chart"]?.firstLines).isEmpty()
        assertThat(entries["Mole.md"]?.kind).isEqualTo(NoteAttachment.Kind.NOTE)
    }
}
