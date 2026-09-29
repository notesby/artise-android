/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.choices

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import kotlinx.collections.immutable.persistentListOf

open class NotesChoicesStatePreviewParam : PreviewParameterProvider<NotesChoicesState> {
    override val values: Sequence<NotesChoicesState>
        get() = sequenceOf(
            aNotesChoicesState(
                aNotesChoiceItem(1, EditState.CONFLICT),
                aNotesChoiceItem(2, EditState.DELETED),
                aNotesChoiceItem(3, EditState.EXISTS),
                aNotesChoiceItem(4, EditState.REJECTED, error = "The note is over 1 MB."),
            ),
            aNotesChoicesState(),
        )
}

fun aNotesChoicesState(vararg items: NotesChoiceItem) = NotesChoicesState(
    items = persistentListOf(*items),
    isLoading = false,
    eventSink = {},
)

fun aNotesChoiceItem(
    editId: Long,
    state: EditState,
    kind: EditKind = EditKind.SAVE,
    error: String? = null,
) = NotesChoiceItem(
    editId = editId,
    path = "Súper $editId.md",
    name = "Súper $editId",
    kind = kind,
    state = state,
    mine = "- leche\n- huevos",
    theirs = "- leche\n- pan",
    error = error,
)
