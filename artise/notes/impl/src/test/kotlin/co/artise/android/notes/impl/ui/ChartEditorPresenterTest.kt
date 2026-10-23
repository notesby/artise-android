/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.api.SyncReport
import co.artise.android.notes.impl.chart.ChartCheck
import co.artise.android.notes.impl.ui.charteditor.ChartEditorEvent
import co.artise.android.notes.impl.ui.charteditor.ChartEditorPresenter
import co.artise.android.notes.impl.ui.charteditor.ChartEditorState
import co.artise.android.notes.impl.ui.common.NoteNames
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale

class ChartEditorPresenterTest {
    private val room = RoomId("!abc:artise.co")
    private val path = "charts/ventas.chart"
    private val mx = Locale.forLanguageTag("es-MX")
    private val ventas = "title: Ventas\ntype: bar\n# Ari's note\ndata:\n  columns: [Mes, Ventas]\n  rows:\n    - [2026-05, 12000]\nlegacy: kept\n"

    private fun repository(content: String? = ventas) =
        FakeNotesRepository(files = mutableMapOf(room to listOfNotNull(content?.let { aNote(path, it) })))

    private fun presenter(repository: FakeNotesRepository, newTitle: String? = null, locale: Locale = mx) =
        ChartEditorPresenter(room, path, newTitle, locale, repository, FakeStringProvider("Columna"))

    private fun ChartEditorState.loaded() = !isLoading

    /** The form round-trips the file: an edit changes only its own line; comments and unknown keys stay. */
    @Test
    fun `saving an edit keeps everything else in the file`() = runTest {
        val repository = repository()
        presenter(repository).test {
            val state = consumeItemsUntilPredicate { it.loaded() }.last()
            assertThat(state.form.title).isEqualTo("Ventas")
            assertThat(state.grid).containsExactly(listOf("2026-05", "12000"))
            state.eventSink(ChartEditorEvent.SetTitle("Ventas de mayo"))
            consumeItemsUntilPredicate { it.form.title == "Ventas de mayo" }.last().eventSink(ChartEditorEvent.Save)
            consumeItemsUntilPredicate { it.isDone }
            assertThat(repository.savedEdits).containsExactly(path to ventas.replace("title: Ventas\n", "title: Ventas de mayo\n"))
            assertThat(repository.syncCount).isAtLeast(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A new chart is written only on Save, from the template, under the path the folder picked. */
    @Test
    fun `a new chart is written only on save`() = runTest {
        val repository = repository(content = null)
        presenter(repository, newTitle = "Ventas").test {
            val state = consumeItemsUntilPredicate { it.loaded() }.last()
            assertThat(state.isNew).isTrue()
            assertThat(state.form.data.columns).containsExactly("Columna", "Columna")
            assertThat(repository.createdNotes).isEmpty()
            assertThat(repository.savedEdits).isEmpty()
            state.eventSink(ChartEditorEvent.SetCell(0, 0, "Mayo"))
            state.eventSink(ChartEditorEvent.SetColumnName(1, "Ventas"))
            consumeItemsUntilPredicate { it.form.data.columns.last() == "Ventas" && it.grid[0][0] == "Mayo" }.last().eventSink(ChartEditorEvent.Save)
            consumeItemsUntilPredicate { it.isDone }
            assertThat(repository.createdNotes)
                .containsExactly(path to "title: Ventas\ntype: bar\ndata:\n  columns: [Columna, Ventas]\n  rows:\n    - [Mayo, 0]\n")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Numbers typed in the person's format are saved plain: `1,234.50` in es-MX and `1.234,50` in pt-BR are `1234.5`. */
    @Test
    fun `numbers are saved in the file's format`() = runTest {
        for ((locale, typed) in listOf(mx to "1,234.50", Locale.forLanguageTag("pt-BR") to "1.234,50")) {
            val repository = repository()
            presenter(repository, locale = locale).test {
                consumeItemsUntilPredicate { it.loaded() }.last().eventSink(ChartEditorEvent.SetCell(0, 1, typed))
                consumeItemsUntilPredicate { it.grid[0][1] == typed }.last().eventSink(ChartEditorEvent.Save)
                consumeItemsUntilPredicate { it.isDone }
                assertThat(repository.savedEdits.single().second).contains("- [2026-05, 1234.5]")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /** A 9th series and a cell that isn't a number are refused on the phone, with the server's rule; Save does nothing. */
    @Test
    fun `local checks refuse what the server would`() = runTest {
        val repository = repository()
        presenter(repository).test {
            val state = consumeItemsUntilPredicate { it.loaded() }.last()
            state.eventSink(ChartEditorEvent.SetCell(0, 1, "mucho"))
            val notANumber = consumeItemsUntilPredicate { it.check != null }.last()
            assertThat(notANumber.check).isEqualTo(ChartCheck.NotANumber(1, "Ventas", "mucho"))
            assertThat(notANumber.canSave).isFalse()
            notANumber.eventSink(ChartEditorEvent.Save)
            notANumber.eventSink(ChartEditorEvent.SetCell(0, 1, "5"))
            val fine = consumeItemsUntilPredicate { it.check == null }.last()
            repeat(8) { fine.eventSink(ChartEditorEvent.AddColumn) }
            val tooMany = consumeItemsUntilPredicate { it.form.data.columns.size == 10 }.last()
            assertThat(tooMany.check).isEqualTo(ChartCheck.TooManySeries)
            assertThat(repository.savedEdits).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The preview waits until typing stops, sends the file's text, and shows the server's sentence when it can't draw it. */
    @Test
    fun `the preview is debounced and shows the server's sentence`() = runTest {
        val repository = repository()
        presenter(repository).test {
            val state = consumeItemsUntilPredicate { it.loaded() }.last()
            consumeItemsUntilPredicate { it.preview != null }
            assertThat(repository.chartPreviewRequests).containsExactly(ventas)
            repository.chartPreviewResult = { Result.failure(NotesException.BadChart("row 1, Ventas: 'x' isn't a number")) }
            state.eventSink(ChartEditorEvent.SetTitle("V"))
            state.eventSink(ChartEditorEvent.SetTitle("Ve"))
            state.eventSink(ChartEditorEvent.SetTitle("Ven"))
            val refused = consumeItemsUntilPredicate { it.serverMessage != null }.last()
            assertThat(refused.serverMessage).isEqualTo("row 1, Ventas: 'x' isn't a number")
            // One drawing for the three changes, of the last text
            assertThat(repository.chartPreviewRequests).hasSize(2)
            assertThat(repository.chartPreviewRequests.last()).startsWith("title: Ven\n")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The server still refuses the save: its sentence shows, the refused edit is dropped, and the work stays in the form. */
    @Test
    fun `a refused save shows the server's sentence and stays open`() = runTest {
        val repository = repository()
        repository.syncResult = { roomId ->
            repository.edits[roomId] = listOf(PendingEdit(7, path, EditKind.SAVE, EditState.REJECTED, "x", null, "the chart needs a title"))
            Result.success(SyncReport(0, 0, 0, 0))
        }
        presenter(repository).test {
            val state = consumeItemsUntilPredicate { it.loaded() }.last()
            state.eventSink(ChartEditorEvent.SetCaption("Sin IVA"))
            consumeItemsUntilPredicate { it.form.caption == "Sin IVA" }.last().eventSink(ChartEditorEvent.Save)
            val refused = consumeItemsUntilPredicate { it.serverMessage != null && !it.isSaving }.last()
            assertThat(refused.serverMessage).isEqualTo("the chart needs a title")
            assertThat(refused.isDone).isFalse()
            assertThat(refused.form.caption).isEqualTo("Sin IVA")
            assertThat(repository.resolved).containsExactly(7L to null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "Show file": the YAML as it will be saved; edited by hand and read back into the form. */
    @Test
    fun `the file's text can be shown and edited`() = runTest {
        val repository = repository()
        presenter(repository).test {
            consumeItemsUntilPredicate { it.loaded() }.last().eventSink(ChartEditorEvent.ShowFile(true))
            val shown = consumeItemsUntilPredicate { it.showFile }.last()
            assertThat(shown.fileText).isEqualTo(ventas)
            shown.eventSink(ChartEditorEvent.SetFileText("title: [oops"))
            val broken = consumeItemsUntilPredicate { it.fileUnreadable }.last()
            // Can't go back to the form while the text can't be read: it stays on the text
            broken.eventSink(ChartEditorEvent.ShowFile(false))
            broken.eventSink(ChartEditorEvent.SetFileText(ventas.replace("type: bar", "type: line")))
            val fixed = consumeItemsUntilPredicate { !it.fileUnreadable }.last()
            assertThat(fixed.showFile).isTrue()
            fixed.eventSink(ChartEditorEvent.ShowFile(false))
            assertThat(consumeItemsUntilPredicate { !it.showFile }.last().form.type).isEqualTo("line")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "New chart" picks a free name in charts/ and refuses names that can't be files. */
    @Test
    fun `new charts get a free name`() {
        assertThat(NoteNames.freeChartPath("Ventas", listOf("Mole.md"))).isEqualTo("charts/Ventas.chart")
        assertThat(NoteNames.freeChartPath("Ventas", listOf("charts/ventas.chart", "charts/Ventas 2.chart"))).isEqualTo("charts/Ventas 3.chart")
        assertThat(NoteNames.freeChartPath(" ", emptyList())).isNull()
        assertThat(NoteNames.freeChartPath("a/b", emptyList())).isNull()
        assertThat(NoteNames.freeChartPath(".oculta", emptyList())).isNull()
    }

    @Test
    fun `colours are set in order`() {
        assertThat(ChartEditorPresenter.colorsWith(emptyList(), 0, "#22408A")).containsExactly("#22408A")
        assertThat(ChartEditorPresenter.colorsWith(listOf("#22408A"), 2, "#EB6834")).containsExactly("#22408A")
        assertThat(ChartEditorPresenter.colorsWith(listOf("#22408A", "#EB6834"), 0, null)).isEmpty()
        assertThat(ChartEditorPresenter.colorsWith(listOf("#22408A", "#EB6834"), 1, "#000000")).containsExactly("#22408A", "#000000").inOrder()
    }
}
