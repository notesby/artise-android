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
    /** [openKind]: the kind the server said, from a card; a dashboard can't be told from its path. */
    fun initialTarget(roomId: RoomId?, openPath: String?, openKind: String? = null): NotesFlowNode.NavTarget {
        if (roomId == null) return NotesFlowNode.NavTarget.Chats
        if (openPath == null) return NotesFlowNode.NavTarget.Folder(roomId, folder = "")
        return targetFor(roomId, openPath, NoteAttachment.kindOf(openPath, openKind))
    }

    fun targetFor(roomId: RoomId, path: String, kind: NoteAttachment.Kind): NotesFlowNode.NavTarget = when (kind) {
        // Read formatted first, with find; the pencil there opens the editor. One that turns out to be a dashboard
        // (an older card said nothing) moves on to it from there.
        NoteAttachment.Kind.NOTE -> NotesFlowNode.NavTarget.Note(roomId, path)
        NoteAttachment.Kind.CHART -> NotesFlowNode.NavTarget.Chart(roomId, path)
        NoteAttachment.Kind.DASHBOARD -> NotesFlowNode.NavTarget.Dashboard(roomId, path)
        NoteAttachment.Kind.PDF,
        NoteAttachment.Kind.IMAGE,
        NoteAttachment.Kind.TABLE,
        NoteAttachment.Kind.OFFICE -> NotesFlowNode.NavTarget.Document(roomId, path)
        // Nothing in the app shows it: its folder, where "Open" hands it to another app.
        NoteAttachment.Kind.OTHER -> NotesFlowNode.NavTarget.Folder(roomId, folder = path.substringBeforeLast('/', ""))
    }
}
