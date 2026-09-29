/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.Backlink
import kotlinx.collections.immutable.ImmutableList

data class NoteState(
    val title: String,
    /** The note's Markdown; `null` while loading or when it isn't on the phone yet. */
    val content: String?,
    val isLoading: Boolean,
    val hasLocalEdits: Boolean,
    val backlinks: BacklinksState,
    /** A tapped `[[link]]` to a note that doesn't exist yet, to explain. */
    val missingNote: String?,
    val eventSink: (NoteEvent) -> Unit,
)

@Immutable
sealed interface BacklinksState {
    data object Loading : BacklinksState

    data class Loaded(val backlinks: ImmutableList<Backlink>) : BacklinksState

    /** Backlinks come from the server, so they need a connection. */
    data object Offline : BacklinksState
}
