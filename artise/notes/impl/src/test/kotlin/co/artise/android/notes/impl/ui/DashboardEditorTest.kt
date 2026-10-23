/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.dashboard.DashboardBlock
import co.artise.android.notes.impl.dashboard.DashboardProblem
import co.artise.android.notes.impl.dashboard.DashboardTheme
import co.artise.android.notes.impl.ui.editor.AttachmentReader
import co.artise.android.notes.impl.ui.editor.ChartChoice
import co.artise.android.notes.impl.ui.editor.NoteEditorEvent
import co.artise.android.notes.impl.ui.editor.NoteEditorNavigator
import co.artise.android.notes.impl.ui.editor.NoteEditorPresenter
import co.artise.android.notes.impl.ui.editor.PickedFile
import co.artise.android.notes.impl.ui.folder.NotesFolderEvent
import co.artise.android.notes.impl.ui.folder.NotesFolderPresenter
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class DashboardEditorTest {
    private val room = RoomId("!abc:artise.co")
    private val clock = FakeSystemClock(epochMillisResult = 1_000_000)
    private val reader = AttachmentReader { Result.success(PickedFile("luna.jpg", "image/jpeg", byteArrayOf(1))) }
    private val dashboard = "---\ntype: dashboard\ntitle: Octubre\n---\n## Ventas\n![[charts/ventas.chart]]\n![[charts/nada.chart]]"

    private fun aChart(path: String, title: String) =
        LocalFile(path, "v", 1, 1, isNote = true, content = "title: $title", hasLocalEdits = false, serverKind = "chart", title = title)

    private fun repository() = FakeNotesRepository(
        files = mutableMapOf(
            room to listOf(aNote("Octubre.md", dashboard), aChart("charts/ventas.chart", "Ventas por mes"), aChart("charts/cobros.chart", "Cobros")),
        ),
    )

    /** A dashboard opens on its layout: blocks, the chart that's missing marked, the chat's charts to add by title. */
    @Test
    fun `a dashboard is laid out with its charts`() = runTest {
        NoteEditorPresenter(room, "Octubre.md", null, {}, repository(), clock, reader).test {
            val state = consumeItemsUntilPredicate { it.dashboard != null }.last()
            val layout = state.dashboard!!
            assertThat(layout.showLayout).isTrue()
            assertThat(layout.blocks.filterIsInstance<DashboardBlock.Chart>().map { it.path }).containsExactly("charts/ventas.chart", "charts/nada.chart")
            assertThat(layout.problems).containsExactly(DashboardProblem.MissingChart(6, "charts/nada.chart"))
            assertThat(
                layout.charts
            ).containsExactly(ChartChoice("charts/cobros.chart", "Cobros"), ChartChoice("charts/ventas.chart", "Ventas por mes")).inOrder()
            // The text is one tap away, as in any note
            state.eventSink(NoteEditorEvent.ToggleDashboardLayout)
            assertThat(consumeItemsUntilPredicate { it.dashboard?.showLayout == false }.last().value.text).isEqualTo(dashboard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A note that isn't a dashboard has no layout. */
    @Test
    fun `an ordinary note has no layout`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Mole.md", "# Mole"))))
        NoteEditorPresenter(room, "Mole.md", null, {}, repository, clock, reader).test {
            assertThat(consumeItemsUntilPredicate { !it.isLoading }.last().dashboard).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** + Chart inserts the exact line; a long press takes one out; the Theme sheet writes valid front matter; each is an undo step. */
    @Test
    fun `layout changes are changes to the text`() = runTest {
        NoteEditorPresenter(room, "Octubre.md", null, {}, repository(), clock, reader).test {
            val state = consumeItemsUntilPredicate { it.dashboard != null }.last()
            state.eventSink(NoteEditorEvent.InsertChart("charts/cobros.chart"))
            val added = consumeItemsUntilPredicate { "cobros" in it.value.text }.last()
            assertThat(added.value.text).isEqualTo("$dashboard\n\n![[charts/cobros.chart]]\n")
            added.eventSink(NoteEditorEvent.RemoveDashboardLine(6))
            val removed = consumeItemsUntilPredicate { "nada" !in it.value.text }.last()
            assertThat(removed.dashboard?.problems).isEmpty()
            removed.eventSink(NoteEditorEvent.SetDashboardTheme(DashboardTheme(accent = "#22408A", palette = persistentListOf("#EB6834"), mode = "dark")))
            val themed = consumeItemsUntilPredicate { "theme:" in it.value.text }.last()
            assertThat(
                themed.value.text
            ).startsWith("---\ntype: dashboard\ntitle: Octubre\ntheme: {accent: \"#22408A\", palette: [\"#EB6834\"], mode: dark}\n---\n")
            assertThat(themed.dashboard?.theme?.mode).isEqualTo("dark")
            assertThat(themed.canUndo).isTrue()
            themed.eventSink(NoteEditorEvent.Undo)
            assertThat(consumeItemsUntilPredicate { "theme:" !in it.value.text }.last().value.text).doesNotContain("nada")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A chart's tile asks for its picture once per mode, and a tap opens the chart. */
    @Test
    fun `chart tiles load their picture and open the chart`() = runTest {
        val picture = Files.createTempFile("chart", ".png").toFile()
        val repository = repository()
        repository.previewResult = { NotesPreview.Image(picture, 512, 288) }
        val opened = mutableListOf<String>()
        val navigator = object : NoteEditorNavigator {
            override fun onDone() = Unit

            override fun openChart(path: String) {
                opened += path
            }
        }
        NoteEditorPresenter(room, "Octubre.md", null, navigator, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { it.dashboard != null }.last()
            state.eventSink(NoteEditorEvent.LoadChartPreview("charts/ventas.chart", dark = true))
            state.eventSink(NoteEditorEvent.LoadChartPreview("charts/ventas.chart", dark = true))
            assertThat(consumeItemsUntilPredicate { it.dashboard?.previews?.isNotEmpty() == true }.last().dashboard?.previews?.get("charts/ventas.chart"))
                .isEqualTo(NotesPreview.Image(picture, 512, 288))
            assertThat(repository.previewRequests).containsExactly("charts/ventas.chart" to 512)
            assertThat(repository.previewModes).containsExactly(true)
            state.eventSink(NoteEditorEvent.OpenChart("charts/ventas.chart"))
            assertThat(opened).containsExactly("charts/ventas.chart")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "New dashboard" makes dashboards/<name>.md with its front matter (a free name) and opens it in the editor. */
    @Test
    fun `a new dashboard is made and opened`() = runTest {
        val opened = mutableListOf<String>()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("dashboards/Octubre.md"))), seenPrivacyNotice = true)
        NotesFolderPresenter(room, "", { opened += it }, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            state.eventSink(NotesFolderEvent.StartNewDashboard)
            consumeItemsUntilPredicate { it.newNote != null }.last().eventSink(NotesFolderEvent.CreateNote(" Octubre "))
            consumeItemsUntilPredicate { it.newNote == null }
            assertThat(repository.createdNotes).containsExactly("dashboards/Octubre 2.md" to "---\ntype: dashboard\ntitle: Octubre\n---\n")
            assertThat(opened).containsExactly("dashboards/Octubre 2.md")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "New chart" opens the chart editor on a free path and writes nothing. */
    @Test
    fun `a new chart opens the editor without writing`() = runTest {
        val newCharts = mutableListOf<Pair<String, String>>()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aChart("charts/Ventas.chart", "Ventas"))), seenPrivacyNotice = true)
        val navigator = object : co.artise.android.notes.impl.ui.folder.NotesFolderNavigator {
            override fun openEditor(path: String) = Unit

            override fun openNewChart(path: String, title: String) {
                newCharts += path to title
            }
        }
        NotesFolderPresenter(room, "Recetas", navigator, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            state.eventSink(NotesFolderEvent.StartNewChart)
            consumeItemsUntilPredicate { it.newNote != null }.last().eventSink(NotesFolderEvent.CreateNote("Ventas"))
            consumeItemsUntilPredicate { it.newNote == null }
            assertThat(newCharts).containsExactly("charts/Ventas 2.chart" to "Ventas")
            assertThat(repository.createdNotes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
