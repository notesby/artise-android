/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

sealed interface NoteEvent {
    /** A `[[link]]` was tapped: open that note, or offer to create it. */
    data class OpenNoteLink(val target: String) : NoteEvent

    /** A checklist item was tapped: tick or untick the item on that line of the note. */
    data class ToggleTask(val lineIndex: Int) : NoteEvent

    /** Create the note a missing link points to, and open it to write. */
    data object CreateMissingNote : NoteEvent

    data object StartRename : NoteEvent

    data class Rename(val newName: String) : NoteEvent

    data object StartDelete : NoteEvent

    data object ConfirmDelete : NoteEvent

    /** Open an attached photo or file in another app. */
    data class OpenAttachment(val path: String) : NoteEvent

    /** Try a waiting or failed upload of a photo or file again now. */
    data class RetryUpload(val path: String) : NoteEvent

    /** Drop a photo or file not uploaded yet (already confirmed), and take it out of the note. */
    data class CancelUpload(val path: String) : NoteEvent

    /** The file was handed to another app ([opened]), or no app could take it. */
    data class FileOpenHandled(val opened: Boolean) : NoteEvent

    data object DismissDialog : NoteEvent
}
