/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chats

import co.artise.android.notes.api.NotesChat
import kotlinx.collections.immutable.ImmutableList

data class NotesChatsState(
    val chats: ImmutableList<NotesChat>,
    val isRefreshing: Boolean,
    val sync: NotesSyncStatus,
    val eventSink: (NotesChatsEvent) -> Unit,
)

/** How the last exchange with the server went, shown as a line under the top bar. */
enum class NotesSyncStatus {
    /** Up to date, or not tried yet. */
    OK,

    /** No connection: showing what's on the phone. */
    OFFLINE,

    /** The server answered with an error. */
    FAILED,

    /** Updated, but some of this phone's changes couldn't be sent yet; they'll be retried. */
    PARTIAL,
}
