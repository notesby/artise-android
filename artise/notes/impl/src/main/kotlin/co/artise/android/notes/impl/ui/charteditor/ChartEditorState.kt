/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.RecordType
import co.artise.android.notes.impl.chart.ChartCheck
import co.artise.android.notes.impl.chart.ChartForm
import co.artise.android.notes.impl.chart.ChartSource
import kotlinx.collections.immutable.ImmutableList

/** The chart editor: a form over the `.chart` file, a live preview, and the file's text for those who want it. */
@Immutable
data class ChartEditorState(
    val isNew: Boolean,
    val isLoading: Boolean,
    /** What the form shows; the grid's cells are as typed ([grid]), numbers in the person's own format. */
    val form: ChartForm,
    val grid: ImmutableList<ImmutableList<String>>,
    /** "Show file": the YAML as it will be saved, editable. */
    val showFile: Boolean,
    val fileText: String,
    /** The file's text can't be shown as the form (not YAML the form reads): only the text, until it's fixed. */
    val fileUnreadable: Boolean,
    /** The first rule the chart breaks, checked on the phone; `null` when the server will draw it. */
    val check: ChartCheck?,
    /** What the server said is wrong (a preview or a save it refused), in its own words. */
    val serverMessage: String?,
    val preview: ChartPreviewImage?,
    val isPreviewing: Boolean,
    /** The record types this chat keeps, for a sourced chart; empty until loaded (or none). */
    val recordTypes: ImmutableList<RecordType>,
    val isSaving: Boolean,
    /** Saved (or nothing to save): the editor closes. */
    val isDone: Boolean,
    val eventSink: (ChartEditorEvent) -> Unit,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && check == null && !fileUnreadable
}

/** The server's drawing of the unsaved chart, as a PNG. */
@Immutable
@Suppress("ArrayInDataClass")
data class ChartPreviewImage(val bytes: ByteArray) {
    override fun equals(other: Any?) = other is ChartPreviewImage && other.bytes.contentEquals(bytes)

    override fun hashCode() = bytes.contentHashCode()
}

sealed interface ChartEditorEvent {
    data class SetTitle(val title: String) : ChartEditorEvent

    data class SetType(val type: String) : ChartEditorEvent

    data class SetUnit(val unit: String) : ChartEditorEvent

    data class SetCaption(val caption: String) : ChartEditorEvent

    /** kpi only: the change against the row before. */
    data class SetCompare(val compare: Boolean) : ChartEditorEvent

    /** The colour of series [index] (from 0); `null` leaves it to the workspace's palette. */
    data class SetColor(val index: Int, val color: String?) : ChartEditorEvent

    /** The Data or Source tab: only one is written. */
    data class UseSource(val useSource: Boolean) : ChartEditorEvent

    data class SetCell(val row: Int, val column: Int, val text: String) : ChartEditorEvent

    data class SetColumnName(val column: Int, val name: String) : ChartEditorEvent

    data object AddRow : ChartEditorEvent

    data class RemoveRow(val row: Int) : ChartEditorEvent

    data object AddColumn : ChartEditorEvent

    data class RemoveColumn(val column: Int) : ChartEditorEvent

    /** Rows copied from a spreadsheet (tabs between cells, a line per row) put in from [row], [column]. */
    data class Paste(val row: Int, val column: Int, val text: String) : ChartEditorEvent

    data class SetSource(val source: ChartSource) : ChartEditorEvent

    data class ShowFile(val show: Boolean) : ChartEditorEvent

    data class SetFileText(val text: String) : ChartEditorEvent

    data object Save : ChartEditorEvent

    data object DismissServerMessage : ChartEditorEvent
}
