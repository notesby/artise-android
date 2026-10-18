/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chats

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.NotesChat
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf

open class NotesChatsStatePreviewParam : PreviewParameterProvider<NotesChatsState> {
    override val values: Sequence<NotesChatsState>
        get() = sequenceOf(
            aNotesChatsState(),
            aNotesChatsState(sync = NotesSyncStatus.OFFLINE),
            aNotesChatsState(chats = emptyList(), isRefreshing = false),
        )
}

fun aNotesChatsState(
    chats: List<NotesChat> = listOf(
        NotesChat(RoomId("!familia:artise.co"), "Familia", "t1"),
        NotesChat(RoomId("!casa:artise.co"), "Casa", "t2"),
    ),
    isRefreshing: Boolean = false,
    sync: NotesSyncStatus = NotesSyncStatus.OK,
) = NotesChatsState(
    chats = persistentListOf(*chats.toTypedArray()),
    isRefreshing = isRefreshing,
    sync = sync,
    eventSink = {},
)
