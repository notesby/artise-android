/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

open class NoteEditorStatePreviewParam : PreviewParameterProvider<NoteEditorState> {
    override val values: Sequence<NoteEditorState>
        get() = sequenceOf(
            // Live preview: the cursor is on "leche", every other line shows formatted.
            aNoteEditorState(cursor = 16),
            aNoteEditorState(
                text = "Para el [[mo",
                cursor = 12,
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
    text: String = "# Súper\n- [ ] leche\n- [x] **pan** integral\nVer [[Recetas/Mole|el mole]]",
    cursor: Int = text.length,
    isResolvingConflict: Boolean = false,
    suggestions: List<WikiLinkSuggestion> = emptyList(),
    showSaveChangesDialog: Boolean = false,
) = NoteEditorState(
    title = "Súper",
    value = TextFieldValue(text, TextRange(cursor)),
    rawLines = LivePreview.rawLines(text, cursor, cursor),
    isLoading = false,
    isResolvingConflict = isResolvingConflict,
    hasUnsavedChanges = false,
    suggestions = persistentListOf(*suggestions.toTypedArray()),
    notePaths = persistentListOf("Recetas/Mole.md", "Súper.md"),
    linkEdit = null,
    isAttaching = false,
    attachError = null,
    canUndo = true,
    canRedo = false,
    showSaveChangesDialog = showSaveChangesDialog,
    eventSink = {},
)
