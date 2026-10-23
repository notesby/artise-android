/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.api.NoteAttachment
import io.element.android.libraries.matrix.api.core.RoomId

/** Where the notes open from outside: the chats, a chat's folder, or straight into one of its files. */
internal object NotesEntry {
    fun initialTarget(roomId: RoomId?, openPath: String?): NotesFlowNode.NavTarget {
        if (roomId == null) return NotesFlowNode.NavTarget.Chats
        if (openPath == null) return NotesFlowNode.NavTarget.Folder(roomId, folder = "")
        return when (NoteAttachment.kindOf(openPath)) {
            // Read formatted first, with find; the pencil there opens the editor.
            NoteAttachment.Kind.NOTE -> NotesFlowNode.NavTarget.Note(roomId, openPath)
            NoteAttachment.Kind.PDF,
            NoteAttachment.Kind.IMAGE,
            NoteAttachment.Kind.TABLE,
            NoteAttachment.Kind.OFFICE -> NotesFlowNode.NavTarget.Document(roomId, openPath)
            // Nothing in the app shows it: its folder, where "Open" hands it to another app.
            NoteAttachment.Kind.OTHER -> NotesFlowNode.NavTarget.Folder(roomId, folder = openPath.substringBeforeLast('/', ""))
        }
    }
}
