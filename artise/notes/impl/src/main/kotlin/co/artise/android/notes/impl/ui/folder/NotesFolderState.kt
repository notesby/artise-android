/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.runtime.Immutable
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import kotlinx.collections.immutable.ImmutableList

data class NotesFolderState(
    /** The chat's name at the top level, else the folder's name. */
    val title: String,
    val entries: ImmutableList<NotesFolderEntry>,
    val isRefreshing: Boolean,
    val sync: NotesSyncStatus,
    /** The one-time "Ari can read notes" notice. */
    val showPrivacyNotice: Boolean,
    /** Edits in this chat waiting for the person to choose a version. */
    val needChoiceCount: Int,
    /** The "new note" name dialog, when open. */
    val newNote: NewNoteDialog?,
    /** The note or file held down: its actions (rename, move, delete) are showing. */
    val actionsFor: NotesFolderEntry?,
    val dialog: FolderDialog?,
    /** The note or file being moved, renamed or deleted right now. */
    val busyPath: String?,
    /** A file to hand to another app, once. */
    val openFile: OpenFileRequest?,
    val eventSink: (NotesFolderEvent) -> Unit,
)

@Immutable
sealed interface FolderDialog {
    data class Rename(val entry: NotesFolderEntry, val problem: NoteNameProblem?) : FolderDialog

    /** Where to move [entry]: the top level ("") or one of [folders]; [current] is where it is now. */
    data class MoveTo(val entry: NotesFolderEntry, val folders: ImmutableList<String>, val current: String) : FolderDialog

    /** The name of a new folder to move [entry] into. */
    data class NewFolder(val entry: NotesFolderEntry, val problem: NoteNameProblem?) : FolderDialog

    data class ConfirmDelete(val entry: NotesFolderEntry) : FolderDialog

    /** A file that notes use: say which, and offer to take it out of them and delete it. */
    data class FileInUse(val entry: NotesFolderEntry, val usedBy: ImmutableList<String>) : FolderDialog

    data class Problem(val problem: FileProblem) : FolderDialog
}

enum class FileProblem {
    /** Moving, renaming and deleting files need a connection. */
    OFFLINE,

    /** Changes not sent yet (or a file still uploading): they must reach the server first. */
    UNSENT,
    EXISTS,
    NO_APP,
    FAILED,
}

/** The name dialog for a new note, with the problem found in the last name tried. */
data class NewNoteDialog(val problem: NoteNameProblem?)

@Immutable
sealed interface NotesFolderEntry {
    val name: String

    val path: String

    /** A subfolder, with how many notes and other files it holds at any depth. */
    data class Folder(override val name: String, override val path: String, val noteCount: Int, val fileCount: Int) : NotesFolderEntry

    /** A note; [hasLocalEdits] marks edits not sent yet. */
    data class Note(override val name: String, override val path: String, val hasLocalEdits: Boolean) : NotesFolderEntry

    /** A photo or other file; [size] in bytes. */
    data class File(override val name: String, override val path: String, val isImage: Boolean, val size: Long) : NotesFolderEntry
}
