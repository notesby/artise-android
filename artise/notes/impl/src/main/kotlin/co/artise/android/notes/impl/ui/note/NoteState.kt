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

data class NoteState(
    val title: String,
    /** The note's Markdown; `null` while loading or when it isn't on the phone yet. */
    val content: String?,
    val isLoading: Boolean,
    val hasLocalEdits: Boolean,
    val backlinks: BacklinksState,
    val dialog: NoteDialog?,
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

@Immutable
sealed interface NoteDialog {
    /** A tapped `[[link]]` to a note that doesn't exist yet: offer to create it. */
    data class MissingNote(val target: String) : NoteDialog

    /** The rename dialog, with the problem found in the last name tried. */
    data class Rename(val currentName: String, val problem: NoteNameProblem?) : NoteDialog

    data object ConfirmDelete : NoteDialog

    data class RenameFailed(val reason: RenameFailure) : NoteDialog
}

enum class RenameFailure {
    /** Renaming happens on the server right away, so it needs a connection. */
    OFFLINE,

    /** The note has changes not sent yet; they must reach the server first. */
    UNSENT_CHANGES,
    OTHER,
}
