/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.media

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.UploadStatus
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import kotlinx.collections.immutable.ImmutableList

data class NotesMediaState(
    val items: ImmutableList<MediaItem>,
    val filter: MediaFilter,
    val unusedCount: Int,
    /** Space the unused photos and files take on the server, in bytes. */
    val unusedBytes: Long,
    val isLoading: Boolean,
    /** The file being deleted right now: its row shows a spinner. */
    val deleting: String?,
    val dialog: MediaDialog?,
    val openFile: OpenFileRequest?,
    val eventSink: (NotesMediaEvent) -> Unit,
)

data class MediaItem(
    val path: String,
    val name: String,
    val isImage: Boolean,
    val size: Long,
    /** The notes that use it, as their paths; empty when unused. */
    val usedBy: ImmutableList<String>,
    val upload: UploadStatus?,
    /** Its copy on the phone, for the thumbnail; `null` when not downloaded. */
    val file: String?,
)

enum class MediaFilter {
    ALL,
    UNUSED,
}

@Immutable
sealed interface MediaDialog {
    /** Delete a file no note uses. */
    data class ConfirmDelete(val item: MediaItem) : MediaDialog

    /** Notes use it: say which, and offer to take it out of them and delete it. */
    data class ConfirmRemoveAndDelete(val item: MediaItem, val usedBy: ImmutableList<String>) : MediaDialog

    data class ConfirmDeleteUnused(val count: Int, val bytes: Long) : MediaDialog

    /** Deleting needs a connection, to be sure no note uses it. */
    data object Offline : MediaDialog

    data object Failed : MediaDialog

    data object NoAppForFile : MediaDialog
}

sealed interface NotesMediaEvent {
    data class SetFilter(val filter: MediaFilter) : NotesMediaEvent

    data class Open(val item: MediaItem) : NotesMediaEvent

    data class FileOpenHandled(val opened: Boolean) : NotesMediaEvent

    data class Delete(val item: MediaItem) : NotesMediaEvent

    /** A file waiting to upload: try again now. */
    data class RetryUpload(val item: MediaItem) : NotesMediaEvent

    data object ConfirmDelete : NotesMediaEvent

    data object DeleteUnused : NotesMediaEvent

    data object ConfirmDeleteUnused : NotesMediaEvent

    data object DismissDialog : NotesMediaEvent
}
