/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.choices

import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import kotlinx.collections.immutable.ImmutableList

data class NotesChoicesState(
    val items: ImmutableList<NotesChoiceItem>,
    val isLoading: Boolean,
    val eventSink: (NotesChoicesEvent) -> Unit,
)

/** One edit waiting for the person: what happened, and both versions when there are two. */
data class NotesChoiceItem(
    val editId: Long,
    val path: String,
    val name: String,
    val kind: EditKind,
    val state: EditState,
    /** This phone's text, for saves. */
    val mine: String?,
    /** The server's text, for conflicts and name clashes. */
    val theirs: String?,
    /** The server's reason, for refused edits. */
    val error: String?,
)

sealed interface NotesChoicesEvent {
    /** Conflict: save this phone's version over the other one. */
    data class KeepMine(val editId: Long) : NotesChoicesEvent

    /** Deleted meanwhile: save this phone's copy again. */
    data class KeepMyCopy(val editId: Long) : NotesChoicesEvent

    /** Same name created meanwhile: keep this phone's note under a new name, and theirs. */
    data class KeepBoth(val editId: Long) : NotesChoicesEvent

    /** Drop this phone's change and go with what the server has. */
    data class Discard(val editId: Long) : NotesChoicesEvent
}
