/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.ui.note.OpenFileRequest

/** A PDF or photo of the chat's notes, read through the Notes API so it is the current version. */
@Immutable
data class NotesDocumentState(
    /** The file's name. */
    val title: String,
    val kind: NoteAttachment.Kind,
    /** The copy on the phone, once downloaded. */
    val file: String?,
    val isLoading: Boolean,
    val problem: DocumentProblem?,
    /** The file to hand to another app, once. */
    val openFile: OpenFileRequest?,
    val eventSink: (NotesDocumentEvent) -> Unit,
)

enum class DocumentProblem {
    /** Not on the phone yet and no connection. */
    OFFLINE,
    FAILED,

    /** "Open with" found no app for this kind of file. */
    NO_APP,
}

sealed interface NotesDocumentEvent {
    data object Retry : NotesDocumentEvent

    /** Hand the file to another app. */
    data object OpenWith : NotesDocumentEvent

    data class FileOpenHandled(val opened: Boolean) : NotesDocumentEvent

    data object DismissProblem : NotesDocumentEvent
}
