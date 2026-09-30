/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.Backlink
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

data class NoteState(
    val title: String,
    /** The note's Markdown; `null` while loading or when it isn't on the phone yet. */
    val content: String?,
    val isLoading: Boolean,
    val hasLocalEdits: Boolean,
    val backlinks: BacklinksState,
    val dialog: NoteDialog?,
    /** Photos and files embedded in the note (`![[...]]`), by the target written in the note. */
    val embeds: ImmutableMap<String, EmbedState>,
    /** A downloaded file to hand to another app, once. */
    val openFile: OpenFileRequest?,
    val eventSink: (NoteEvent) -> Unit,
) {
    /** Only a note whose text is on the phone can be edited. */
    val canEdit: Boolean get() = content != null
}

@Immutable
sealed interface BacklinksState {
    data object Loading : BacklinksState

    data class Loaded(val backlinks: ImmutableList<Backlink>) : BacklinksState

    /** Backlinks come from the server, so they need a connection. */
    data object Offline : BacklinksState
}

/** An embedded photo or file: [file] is its copy on the phone once downloaded (photos download by themselves). */
data class EmbedState(
    val path: String,
    val name: String,
    val isImage: Boolean,
    val file: String?,
    val failed: Boolean,
    /** In bytes, when known; shown on document cards. */
    val size: Long? = null,
    /** Being downloaded to open it. */
    val isDownloading: Boolean = false,
)

/** Open [file] (on the phone) in the app the person uses for that kind of file. */
data class OpenFileRequest(val file: String, val name: String)

@Immutable
sealed interface NoteDialog {
    /** A tapped `[[link]]` to a note that doesn't exist yet: offer to create it. */
    data class MissingNote(val target: String) : NoteDialog

    /** The rename dialog, with the problem found in the last name tried. */
    data class Rename(val currentName: String, val problem: NoteNameProblem?) : NoteDialog

    data object ConfirmDelete : NoteDialog

    data class RenameFailed(val reason: RenameFailure) : NoteDialog

    /** A photo or file couldn't be downloaded (usually no connection). */
    data object AttachmentUnavailable : NoteDialog

    /** No app on the phone opens this kind of file. */
    data object NoAppForFile : NoteDialog
}

enum class RenameFailure {
    /** Renaming happens on the server right away, so it needs a connection. */
    OFFLINE,

    /** The note has changes not sent yet; they must reach the server first. */
    UNSENT_CHANGES,
    OTHER,
}
