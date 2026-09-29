/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.runtime.Immutable
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import kotlinx.collections.immutable.ImmutableList

data class NotesFolderState(
    /** The chat's name at the top level, else the folder's name. */
    val title: String,
    val entries: ImmutableList<NotesFolderEntry>,
    val isRefreshing: Boolean,
    val sync: NotesSyncStatus,
    /** The one-time "Ari can read notes" notice. */
    val showPrivacyNotice: Boolean,
    val eventSink: (NotesFolderEvent) -> Unit,
)

@Immutable
sealed interface NotesFolderEntry {
    val name: String

    /** A subfolder, with how many notes it holds at any depth. */
    data class Folder(override val name: String, val path: String, val noteCount: Int) : NotesFolderEntry

    /** A note; [hasLocalEdits] marks edits not sent yet. */
    data class Note(override val name: String, val path: String, val hasLocalEdits: Boolean) : NotesFolderEntry
}
