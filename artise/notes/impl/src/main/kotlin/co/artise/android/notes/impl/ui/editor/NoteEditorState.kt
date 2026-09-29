/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.collections.immutable.ImmutableList

data class NoteEditorState(
    val title: String,
    /** The note's Markdown and the cursor. */
    val value: TextFieldValue,
    /** Lines shown as written; every other line shows formatted (live preview). */
    val rawLines: IntRange,
    val isLoading: Boolean,
    /** Combining two versions after someone else edited the same lines. */
    val isResolvingConflict: Boolean,
    val hasUnsavedChanges: Boolean,
    /** Notes to link while typing `[[`; empty when not in a link. */
    val suggestions: ImmutableList<WikiLinkSuggestion>,
    /** The chat's notes, for the link dialog's suggestions. */
    val notePaths: ImmutableList<String>,
    /** The link dialog, after tapping a link on a formatted line. */
    val linkEdit: LinkEditState?,
    val showSaveChangesDialog: Boolean,
    val eventSink: (NoteEditorEvent) -> Unit,
)

/** A link being edited: where it is in the Markdown, and what it says now. */
data class LinkEditState(
    val start: Int,
    val end: Int,
    val link: EditableLink,
)
