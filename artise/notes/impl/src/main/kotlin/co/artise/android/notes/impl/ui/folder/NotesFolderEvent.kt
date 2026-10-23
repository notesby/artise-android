/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.NoteAttachment

sealed interface NotesFolderEvent {
    data object Refresh : NotesFolderEvent

    data object DismissPrivacyNotice : NotesFolderEvent

    data object StartNewNote : NotesFolderEvent

    /** A new chart: its name is asked, then the chart editor opens (nothing is written until Save). */
    data object StartNewChart : NotesFolderEvent

    /** A new dashboard: its name is asked, then it's made in `dashboards/` and opens in the editor. */
    data object StartNewDashboard : NotesFolderEvent

    /** Create a note called [name] in this folder and open it in the editor; for a new chart, open the chart editor. */
    data class CreateNote(val name: String) : NotesFolderEvent

    data object CancelNewNote : NotesFolderEvent

    /** A long press on a note or file: show what can be done with it. */
    data class ShowActions(val entry: NotesFolderEntry) : NotesFolderEvent

    data object DismissActions : NotesFolderEvent

    /** Open a photo or file in another app. */
    data class OpenFile(val entry: NotesFolderEntry) : NotesFolderEvent

    /** A photo or document's row is on screen: fetch what it looks like ([dark]: a chart or dashboard drawn for a dark screen). */
    data class LoadPreview(val path: String, val dark: Boolean = false, val kind: NoteAttachment.Kind? = null) : NotesFolderEvent

    data class FileOpenHandled(val opened: Boolean) : NotesFolderEvent

    data object StartRename : NotesFolderEvent

    data class Rename(val newName: String) : NotesFolderEvent

    data object StartMove : NotesFolderEvent

    /** Move into [folder] ("" for the top level). */
    data class MoveTo(val folder: String) : NotesFolderEvent

    data object StartNewFolder : NotesFolderEvent

    /** Move into a new folder called [name] (it can hold "/" for a folder inside another). */
    data class MoveToNewFolder(val name: String) : NotesFolderEvent

    data object StartDelete : NotesFolderEvent

    data object ConfirmDelete : NotesFolderEvent

    /** The file is used in notes: take it out of them, then delete it. */
    data object ConfirmRemoveAndDelete : NotesFolderEvent

    data object DismissDialog : NotesFolderEvent

    /** The "+" button: choose between a new note and a new folder. */
    data object ShowNewMenu : NotesFolderEvent

    data object DismissNewMenu : NotesFolderEvent

    data object StartCreateFolder : NotesFolderEvent

    /** Create an empty folder called [name] here ("/" for one inside another). */
    data class CreateFolder(val name: String) : NotesFolderEvent

    /** Delete this folder, now that it's empty. */
    data object DeleteThisFolder : NotesFolderEvent
}
