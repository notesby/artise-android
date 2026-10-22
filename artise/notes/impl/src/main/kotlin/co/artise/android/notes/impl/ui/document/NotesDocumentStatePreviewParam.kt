/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.NoteAttachment

open class NotesDocumentStatePreviewParam : PreviewParameterProvider<NotesDocumentState> {
    override val values: Sequence<NotesDocumentState>
        get() = sequenceOf(
            aNotesDocumentState(),
            aNotesDocumentState(isLoading = false, problem = DocumentProblem.OFFLINE),
            aNotesDocumentState(isLoading = false, problem = DocumentProblem.FAILED),
            aNotesDocumentState(title = "luna.jpg", kind = NoteAttachment.Kind.IMAGE, isLoading = false, problem = DocumentProblem.NO_APP),
        )
}

fun aNotesDocumentState(
    title: String = "INV-2026-004.pdf",
    kind: NoteAttachment.Kind = NoteAttachment.Kind.PDF,
    file: String? = null,
    isLoading: Boolean = true,
    problem: DocumentProblem? = null,
) = NotesDocumentState(
    title = title,
    kind = kind,
    file = file,
    isLoading = isLoading,
    problem = problem,
    openFile = null,
    eventSink = {},
)
