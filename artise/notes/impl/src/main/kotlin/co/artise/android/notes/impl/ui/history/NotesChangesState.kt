/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import co.artise.android.notes.impl.ui.note.OpenFileRequest
import kotlinx.collections.immutable.ImmutableList

/** A chat's change history: every change to its notes, newest first, grouped by day. */
data class NotesChangesState(
    val days: ImmutableList<HistoryDay>,
    /** The first page is on its way. */
    val isLoading: Boolean,
    /** An older page is on its way. */
    val isLoadingMore: Boolean,
    /** Older changes can be asked for. */
    val hasMore: Boolean,
    /** The last page couldn't be loaded (usually no connection). */
    val failed: Boolean,
    /** The photo or document being downloaded to open it. */
    val busyPath: String?,
    /** A downloaded file to hand to another app, once. */
    val openFile: OpenFileRequest?,
    /** A file couldn't be opened: no connection, or no app for it. */
    val fileProblem: HistoryFileProblem?,
    val eventSink: (NotesChangesEvent) -> Unit,
)

enum class HistoryFileProblem {
    OFFLINE,
    NO_APP,
}

sealed interface NotesChangesEvent {
    /** The end of the list is near: load the next older page. */
    data object LoadMore : NotesChangesEvent

    /** Try the page that failed again. */
    data object Retry : NotesChangesEvent

    /** Download a photo or document the history shows, to open it. */
    data class OpenFile(val path: String, val name: String) : NotesChangesEvent

    /** The file was handed to another app ([opened]), or no app could take it. */
    data class FileOpenHandled(val opened: Boolean) : NotesChangesEvent

    data object DismissFileProblem : NotesChangesEvent
}
