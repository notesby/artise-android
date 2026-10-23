/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.api.RecordType
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.chart.ChartChecks
import co.artise.android.notes.impl.chart.ChartData
import co.artise.android.notes.impl.chart.ChartFile
import co.artise.android.notes.impl.chart.ChartForm
import co.artise.android.notes.impl.chart.ChartNumbers
import co.artise.android.notes.impl.chart.ChartSource
import co.artise.android.notes.impl.chart.ChartType
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The chart editor: reads the `.chart` file into a form (or starts a new one, written only on the first Save), checks
 * the server's rules on the phone as the person types, and shows the server's drawing of the unsaved chart about
 * [PREVIEW_DELAY_MS] after typing stops. Saving goes through the notes like any edit; a chart the server still refuses
 * comes back with its sentence and the editor stays open.
 */
@AssistedInject
class ChartEditorPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    /** Set for a new chart: its name, which is also its first title. */
    @Assisted private val newTitle: String?,
    /** How the person types numbers (`1.234,50` in pt-BR). */
    @Assisted private val locale: Locale,
    private val repository: NotesRepository,
    private val stringProvider: StringProvider,
) : Presenter<ChartEditorState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String, newTitle: String?, locale: Locale): ChartEditorPresenter
    }

    @Composable
    override fun present(): ChartEditorState {
        val scope = rememberCoroutineScope()
        val dark = !ElementTheme.isLightTheme
        var isLoading by remember { mutableStateOf(true) }
        // What was read (or the new chart's template): writing keeps everything the form didn't change.
        var file by remember { mutableStateOf<ChartFile?>(null) }
        var savedText by remember { mutableStateOf<String?>(null) }
        var form by remember { mutableStateOf(ChartForm()) }
        var grid by remember { mutableStateOf<List<List<String>>>(emptyList()) }
        var showFile by remember { mutableStateOf(false) }
        var fileText by remember { mutableStateOf("") }
        var fileUnreadable by remember { mutableStateOf(false) }
        var serverMessage by remember { mutableStateOf<String?>(null) }
        var preview by remember { mutableStateOf<ChartPreviewImage?>(null) }
        var isPreviewing by remember { mutableStateOf(false) }
        var previewOffline by remember { mutableStateOf(false) }
        var recordTypes by remember { mutableStateOf<List<RecordType>>(emptyList()) }
        var isSaving by remember { mutableStateOf(false) }
        var isDone by remember { mutableStateOf(false) }

        fun show(read: ChartFile) {
            file = read
            form = read.form
            grid = read.form.data.rows.map { row -> row.map { ChartNumbers.forEditing(it, locale) } }
        }

        LaunchedEffect(Unit) {
            val text = if (newTitle != null) {
                ChartFile.newText(
                    title = newTitle.trim(),
                    labelColumn = stringProvider.getString(R.string.screen_notes_chart_column_labels),
                    valueColumn = stringProvider.getString(R.string.screen_notes_chart_column_values),
                )
            } else {
                repository.file(roomId, path)?.content ?: run {
                    // Not downloaded yet: a sync fetches it, when there's a connection.
                    repository.sync(roomId)
                    repository.file(roomId, path)?.content
                }.orEmpty()
            }
            savedText = text.takeIf { newTitle == null }
            ChartFile.read(text).fold(
                onSuccess = ::show,
                onFailure = {
                    // Not something the form can show: the file's text, to fix by hand.
                    file = ChartFile.read("").getOrNull()
                    showFile = true
                    fileText = text
                    fileUnreadable = true
                },
            )
            isLoading = false
        }
        LaunchedEffect(Unit) {
            recordTypes = repository.recordTypes(roomId).getOrDefault(emptyList())
        }

        // The form with the grid's numbers as the file writes them.
        val current = form.copy(data = ChartData(form.data.columns, grid.map { row -> row.mapIndexed { i, cell -> fileCell(i, cell) } }))
        val text = if (showFile) fileText else file?.write(current).orEmpty()
        val shown = if (showFile) ChartFile.read(fileText).getOrNull()?.form else current
        val check = if (isLoading || shown == null) null else ChartChecks.check(shown, text)

        val isReady = !isLoading && !fileUnreadable
        val canPreview = isReady && check == null && text.isNotBlank()
        val canSave = isReady && check == null && !isSaving
        LaunchedEffect(text, dark, canPreview) {
            if (!canPreview) return@LaunchedEffect
            // Each new version is a drawing on the server: only once typing has stopped.
            delay(PREVIEW_DELAY_MS)
            isPreviewing = true
            repository.chartPreview(roomId, text, PREVIEW_SIZE, dark).fold(
                onSuccess = { bytes ->
                    preview = ChartPreviewImage(bytes)
                    serverMessage = null
                    previewOffline = false
                },
                onFailure = { error ->
                    if (error is NotesException.BadChart) serverMessage = error.message
                    previewOffline = error is NotesException.Network
                },
            )
            isPreviewing = false
        }

        fun updateGrid(change: (MutableList<MutableList<String>>) -> Unit) {
            grid = grid.map { it.toMutableList() }.toMutableList().also(change)
        }

        fun applyEvent(event: ChartEditorEvent) {
            when (event) {
                is ChartEditorEvent.SetTitle -> form = form.copy(title = event.title)
                is ChartEditorEvent.SetType -> form =
                    form.copy(type = event.type, compare = form.compare.takeIf { event.type == ChartType.KPI.value }.orEmpty())
                is ChartEditorEvent.SetUnit -> form = form.copy(unit = event.unit)
                is ChartEditorEvent.SetCaption -> form = form.copy(caption = event.caption)
                is ChartEditorEvent.SetCompare -> form = form.copy(compare = if (event.compare) COMPARE_PREVIOUS else "")
                is ChartEditorEvent.SetColor -> form = form.copy(colors = colorsWith(form.colors, event.index, event.color, event.palette))
                is ChartEditorEvent.UseSource -> form = form.copy(
                    usesSource = event.useSource,
                    source = if (event.useSource) form.source.withDefaults(recordTypes) else form.source,
                )
                is ChartEditorEvent.SetCell -> updateGrid { rows -> rows.getOrNull(event.row)?.set(event.column, event.text) }
                is ChartEditorEvent.SetColumnName -> {
                    val old = form.data.columns.getOrNull(event.column) ?: return
                    form = form.copy(
                        data = form.data.copy(columns = form.data.columns.toMutableList().also { it[event.column] = event.name }),
                        series = form.series.map { if (it == old) event.name else it },
                    )
                }
                ChartEditorEvent.AddRow -> updateGrid { rows -> rows += MutableList(form.data.columns.size) { if (it == 0) "" else "0" } }
                is ChartEditorEvent.RemoveRow -> updateGrid { rows -> if (event.row in rows.indices) rows.removeAt(event.row) }
                ChartEditorEvent.AddColumn -> {
                    form = form.copy(data = form.data.copy(columns = form.data.columns + newColumnName(form.data.columns)))
                    updateGrid { rows -> rows.forEach { it += "0" } }
                }
                is ChartEditorEvent.RemoveColumn -> {
                    val name = form.data.columns.getOrNull(event.column) ?: return
                    if (event.column == 0) return
                    form = form.copy(
                        data = form.data.copy(columns = form.data.columns.filterIndexed { i, _ -> i != event.column }),
                        series = form.series - name,
                    )
                    updateGrid { rows -> rows.forEach { if (event.column < it.size) it.removeAt(event.column) } }
                }
                is ChartEditorEvent.Paste -> {
                    // Rows from a spreadsheet replace the table; a first line with no numbers after its first cell names the columns.
                    val pasted = ChartPaste.table(event.text) ?: return
                    val width = maxOf(pasted.columns?.size ?: 0, pasted.rows.maxOfOrNull { it.size } ?: 0, 2)
                    var columns = pasted.columns.orEmpty().take(width)
                    while (columns.size < width) columns = columns + newColumnName(columns)
                    form = form.copy(data = ChartData(columns, emptyList()), series = form.series.filter { it in columns })
                    grid = pasted.rows.map { row -> List(width) { row.getOrNull(it).orEmpty() } }
                }
                is ChartEditorEvent.SetSource -> form = form.copy(source = event.source)
                is ChartEditorEvent.ShowFile -> if (event.show) {
                    fileText = text
                    showFile = true
                } else {
                    ChartFile.read(fileText).onSuccess { read ->
                        show(read)
                        showFile = false
                        fileUnreadable = false
                    }.onFailure { fileUnreadable = true }
                }
                is ChartEditorEvent.SetFileText -> {
                    fileText = event.text
                    fileUnreadable = ChartFile.read(event.text).isFailure
                }
                ChartEditorEvent.Save -> if (canSave) {
                    if (text == savedText) {
                        isDone = true
                        return
                    }
                    isSaving = true
                    scope.launch {
                        // Saved on the phone and sent now, or when there's a connection. A chart the server still refuses
                        // becomes a refused edit with its sentence, in the choices screen, like any other.
                        val written = if (newTitle == null) {
                            repository.editNote(roomId, path, text)
                            Result.success(Unit)
                        } else {
                            repository.createNote(roomId, path, text)
                        }
                        written.fold(
                            onSuccess = {
                                repository.syncInBackground(roomId)
                                savedText = text
                                isDone = true
                            },
                            onFailure = { serverMessage = it.message },
                        )
                        isSaving = false
                    }
                }
                ChartEditorEvent.DismissServerMessage -> serverMessage = null
            }
        }

        // One event's changes (the form and the grid) land together.
        fun handle(event: ChartEditorEvent) {
            Snapshot.withMutableSnapshot { applyEvent(event) }
        }

        return ChartEditorState(
            isNew = newTitle != null,
            isLoading = isLoading,
            form = form,
            grid = grid.map { it.toImmutableList() }.toImmutableList(),
            showFile = showFile,
            fileText = if (showFile) fileText else text,
            fileUnreadable = fileUnreadable,
            check = check,
            serverMessage = serverMessage,
            preview = preview,
            isPreviewing = isPreviewing,
            previewOffline = previewOffline,
            recordTypes = recordTypes.toImmutableList(),
            isSaving = isSaving,
            isDone = isDone,
            eventSink = ::handle,
        )
    }

    /** A series' cell as the file writes it: a number typed in the person's format becomes `1234.5`; anything else stays as typed. */
    private fun fileCell(column: Int, cell: String): String = if (column == 0) cell else ChartNumbers.toFile(cell, locale) ?: cell

    private fun newColumnName(columns: List<String>): String =
        generateSequence(columns.size) { it + 1 }
            .map { stringProvider.getString(R.string.screen_notes_chart_column_new, it) }
            .first { it !in columns }

    private fun ChartSource.withDefaults(types: List<RecordType>): ChartSource {
        val type = types.firstOrNull { it.type == records } ?: types.firstOrNull()
        return copy(
            records = records.ifBlank { type?.type.orEmpty() },
            period = period.ifBlank { DEFAULT_PERIOD },
            x = x.ifBlank { DEFAULT_X },
            y = y.ifBlank { DEFAULT_Y },
        )
    }

    companion object {
        const val PREVIEW_DELAY_MS = 600L
        const val PREVIEW_SIZE = 512
        const val COMPARE_PREVIOUS = "previous"
        const val DEFAULT_PERIOD = "last 6 months"
        const val DEFAULT_X = "month"
        const val DEFAULT_Y = "count"

        /**
         * Series [index]'s colour set to [color]. The file's colours go to the series in order, so the ones before it that
         * have none take the workspace's [palette] colours (else stay as Artise's). `null` ("Workspace colours") clears them all.
         */
        fun colorsWith(colors: List<String>, index: Int, color: String?, palette: List<String>): List<String> {
            if (color == null) return emptyList()
            val filled = colors.toMutableList()
            while (filled.size < index) filled += palette.getOrNull(filled.size % palette.size.coerceAtLeast(1)) ?: return colors
            if (index < filled.size) filled[index] = color else filled += color
            return filled
        }
    }
}
