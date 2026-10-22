/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.ui.find.FindMatch
import kotlinx.collections.immutable.persistentListOf

open class NotesDocumentStatePreviewParam : PreviewParameterProvider<NotesDocumentState> {
    override val values: Sequence<NotesDocumentState>
        get() = sequenceOf(
            aNotesDocumentState(),
            aNotesDocumentState(isLoading = false, problem = DocumentProblem.OFFLINE),
            aNotesDocumentState(isLoading = false, problem = DocumentProblem.FAILED),
            aNotesDocumentState(title = "luna.jpg", kind = NoteAttachment.Kind.IMAGE, isLoading = false, problem = DocumentProblem.NO_APP),
            aNotesDocumentState(
                isLoading = false,
                find = DocumentFind(
                    query = "mole",
                    pages = persistentListOf("Mole poblano con chiles", "Más mole"),
                    matches = persistentListOf(FindMatch(0, 0, 4), FindMatch(1, 4, 8)),
                    current = 1,
                    isLoading = false,
                    isUnavailable = false,
                ),
            ),
            aNotesDocumentState(
                isLoading = false,
                find = DocumentFind("", persistentListOf(), persistentListOf(), 0, isLoading = false, isUnavailable = true),
            ),
        )
}

fun aNotesDocumentState(
    title: String = "INV-2026-004.pdf",
    kind: NoteAttachment.Kind = NoteAttachment.Kind.PDF,
    file: String? = null,
    isLoading: Boolean = true,
    problem: DocumentProblem? = null,
    find: DocumentFind? = null,
) = NotesDocumentState(
    title = title,
    kind = kind,
    file = file,
    isLoading = isLoading,
    problem = problem,
    openFile = null,
    find = find,
    eventSink = {},
)
