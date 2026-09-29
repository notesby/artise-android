/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

open class NoteEditorStatePreviewParam : PreviewParameterProvider<NoteEditorState> {
    override val values: Sequence<NoteEditorState>
        get() = sequenceOf(
            aNoteEditorState(),
            aNoteEditorState(
                text = "Para el [[mo",
                suggestions = listOf(
                    WikiLinkSuggestion("Mole", "Recetas/Mole.md", "Recetas/Mole"),
                    WikiLinkSuggestion("Mole", "Viejo/Mole.md", "Viejo/Mole"),
                ),
            ),
            aNoteEditorState(text = "- leche" + NoteEditorPresenter.CONFLICT_SEPARATOR + "- pan", isResolvingConflict = true),
            aNoteEditorState(showSaveChangesDialog = true),
        )
}

fun aNoteEditorState(
    text: String = "# Súper\n- leche\n- pan",
    isResolvingConflict: Boolean = false,
    suggestions: List<WikiLinkSuggestion> = emptyList(),
    showSaveChangesDialog: Boolean = false,
) = NoteEditorState(
    title = "Súper",
    text = TextFieldState(text),
    isLoading = false,
    isResolvingConflict = isResolvingConflict,
    hasUnsavedChanges = false,
    suggestions = persistentListOf(*suggestions.toTypedArray()),
    showSaveChangesDialog = showSaveChangesDialog,
    eventSink = {},
)
