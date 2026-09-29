/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.collections.immutable.ImmutableList

data class NoteEditorState(
    val title: String,
    val text: TextFieldState,
    val isLoading: Boolean,
    /** Combining two versions after someone else edited the same lines. */
    val isResolvingConflict: Boolean,
    val hasUnsavedChanges: Boolean,
    /** Notes to link while typing `[[`; empty when not in a link. */
    val suggestions: ImmutableList<WikiLinkSuggestion>,
    val showSaveChangesDialog: Boolean,
    val eventSink: (NoteEditorEvent) -> Unit,
)
