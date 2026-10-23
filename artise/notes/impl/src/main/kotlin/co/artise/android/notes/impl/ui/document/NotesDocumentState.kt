/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.ui.find.FindMatch
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import kotlinx.collections.immutable.ImmutableList

/** A PDF, photo, CSV or Office file of the chat's notes, read through the Notes API so it is the current version. */
@Immutable
data class NotesDocumentState(
    /** The file's name. */
    val title: String,
    val kind: NoteAttachment.Kind,
    /** The copy on the phone, once downloaded. */
    val file: String?,
    /** What the PDF viewer shows: the file itself for a PDF, the server's conversion for an Office file, once there. */
    val pdfFile: String? = null,
    /** A CSV's text, once read. */
    val tableText: String? = null,
    val isLoading: Boolean,
    val problem: DocumentProblem?,
    /** The file to hand to another app, once. */
    val openFile: OpenFileRequest?,
    /** The find bar, while open. */
    val find: DocumentFind?,
    val eventSink: (NotesDocumentEvent) -> Unit,
) {
    /** Finding is on offer once there's something to search: a PDF (or an Office file's conversion), or a CSV's text. */
    val canFind: Boolean get() = file != null && (pdfFile != null || tableText != null)
}

/**
 * Finding in a document: the server's text page by page ([pages], index = page from 0), what's typed, every match
 * and the one in view. [isUnavailable] when the server has no text for this file (too big, scanned, another kind).
 */
@Immutable
data class DocumentFind(
    val query: String,
    val pages: ImmutableList<String>,
    val matches: ImmutableList<FindMatch>,
    val current: Int,
    val isLoading: Boolean,
    val isUnavailable: Boolean,
) {
    val currentMatch: FindMatch? get() = matches.getOrNull(current)
}

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

    data object OpenFind : NotesDocumentEvent

    data class FindQueryChanged(val query: String) : NotesDocumentEvent

    data object FindNext : NotesDocumentEvent

    data object FindPrevious : NotesDocumentEvent

    data object CloseFind : NotesDocumentEvent
}
