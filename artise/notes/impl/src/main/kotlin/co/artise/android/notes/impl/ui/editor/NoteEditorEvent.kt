/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

sealed interface NoteEditorEvent {
    data class SelectSuggestion(val suggestion: WikiLinkSuggestion) : NoteEditorEvent

    /** A toolbar button: format the selection or the current lines. */
    data class Format(val action: FormatAction) : NoteEditorEvent

    data object Save : NoteEditorEvent

    /** Back pressed: leave, or ask first when there are unsaved changes. */
    data object Back : NoteEditorEvent

    data object DiscardChanges : NoteEditorEvent

    data object DismissSaveChangesDialog : NoteEditorEvent
}
