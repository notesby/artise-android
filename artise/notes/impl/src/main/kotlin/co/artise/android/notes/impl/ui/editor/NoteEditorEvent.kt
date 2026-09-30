/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.text.input.TextFieldValue

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

    data object Undo : NoteEditorEvent

    data object Redo : NoteEditorEvent

    data object Save : NoteEditorEvent

    /** Back pressed: leave, or ask first when there are unsaved changes. */
    data object Back : NoteEditorEvent

    data object DiscardChanges : NoteEditorEvent

    data object DismissSaveChangesDialog : NoteEditorEvent
}
