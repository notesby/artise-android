/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import co.artise.android.notes.impl.dashboard.DashboardTheme

sealed interface NoteEditorEvent {
    /** Typing, or the cursor moving (including a tap, which may hit a checkbox or a link). */
    data class ValueChanged(val value: TextFieldValue) : NoteEditorEvent

    data class SelectSuggestion(val suggestion: WikiLinkSuggestion) : NoteEditorEvent

    /** A toolbar button: format the selection or the current lines. */
    data class Format(val action: FormatAction) : NoteEditorEvent

    /** The link dialog's result: write [link] in place of the link that was tapped. */
    data class SaveLink(val link: EditableLink) : NoteEditorEvent

    /** Remove the tapped link, keeping the text people saw. */
    data object RemoveLink : NoteEditorEvent

    data object DismissLinkEdit : NoteEditorEvent

    /** The person picked a photo or file ([uri] from the Android picker) to add at the cursor. */
    data class Attach(val uri: String) : NoteEditorEvent

    data object DismissAttachError : NoteEditorEvent

    /** Switch between editing the text and previewing the finished note. */
    data object TogglePreview : NoteEditorEvent

    /** The find bar: open it, type in it, move between matches, close it. */
    data object OpenFind : NoteEditorEvent

    data class FindQueryChanged(val query: String) : NoteEditorEvent

    data object FindNext : NoteEditorEvent

    data object FindPrevious : NoteEditorEvent

    data object CloseFind : NoteEditorEvent

    /** In the preview, a checklist item was tapped: tick it in the text. */
    data class ToggleTask(val lineIndex: Int) : NoteEditorEvent

    /** A photo in the strip or the preview was tapped: open it in another app. */
    data class OpenAttachment(val path: String) : NoteEditorEvent

    /** Try a waiting or failed upload of a photo or file again now. */
    data class RetryUpload(val path: String) : NoteEditorEvent

    /** Drop a photo or file not uploaded yet (already confirmed), and take it out of the text. */
    data class CancelUpload(val path: String) : NoteEditorEvent

    data class FileOpenHandled(val opened: Boolean) : NoteEditorEvent

    data object DismissOpenFileProblem : NoteEditorEvent

    data object Undo : NoteEditorEvent

    data object Redo : NoteEditorEvent

    data object Save : NoteEditorEvent

    /** Back pressed: leave, or ask first when there are unsaved changes. */
    data object Back : NoteEditorEvent

    data object DiscardChanges : NoteEditorEvent

    data object DismissSaveChangesDialog : NoteEditorEvent

    /** A dashboard: between the layout (charts drawn) and the Markdown text. */
    data object ToggleDashboardLayout : NoteEditorEvent

    /** The text block starting at [line] was tapped in the layout: the text, with the cursor there. */
    data class EditDashboardText(val line: Int) : NoteEditorEvent

    /** Takes a chart's line out of the dashboard (the chart itself stays in the notes). */
    data class RemoveDashboardLine(val line: Int) : NoteEditorEvent

    /** + Chart: `![[path]]` on a line of its own at the end. */
    data class InsertChart(val path: String) : NoteEditorEvent

    /** Moves the chart on [line] one place within its columns block: [delta] -1 or +1. */
    data class MoveDashboardChart(val line: Int, val delta: Int) : NoteEditorEvent

    /** Writes [theme] into the front matter (or takes it out when it's empty). */
    data class SetDashboardTheme(val theme: DashboardTheme) : NoteEditorEvent

    data class OpenChart(val path: String) : NoteEditorEvent

    /** A chart's tile is on screen: fetch its picture for the screen's light or [dark]. */
    data class LoadChartPreview(val path: String, val dark: Boolean) : NoteEditorEvent
}
