/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import kotlinx.collections.immutable.persistentListOf

open class NotesFolderStatePreviewParam : PreviewParameterProvider<NotesFolderState> {
    override val values: Sequence<NotesFolderState>
        get() = sequenceOf(
            aNotesFolderState(),
            aNotesFolderState(showPrivacyNotice = true),
            aNotesFolderState(sync = NotesSyncStatus.OFFLINE),
            aNotesFolderState(entries = emptyList()),
            aNotesFolderState(needChoiceCount = 2),
            aNotesFolderState(newNote = NewNoteDialog(problem = NoteNameProblem.EXISTS)),
            aNotesFolderState(actionsFor = NotesFolderEntry.File("factura.pdf", "factura.pdf", isImage = false, size = 46_000)),
            aNotesFolderState(
                dialog = FolderDialog.MoveTo(
                    entry = NotesFolderEntry.Note("Súper", "Súper.md", hasLocalEdits = false),
                    folders = persistentListOf("Recetas", "Recetas/Postres", "Viajes"),
                    current = "",
                ),
            ),
            aNotesFolderState(showNewMenu = true),
            aNotesFolderState(
                dialog = FolderDialog.ConfirmDeleteFolder(NotesFolderEntry.Folder("Recetas", "Recetas", 12, 3), notes = 12, files = 3, folders = 1),
            ),
            aNotesFolderState(title = "Viajes", entries = emptyList(), canDeleteFolder = true),
        )
}

fun aNotesFolderState(
    title: String = "Familia",
    entries: List<NotesFolderEntry> = listOf(
        NotesFolderEntry.Folder("Recetas", "Recetas", 12, 3),
        NotesFolderEntry.Note("Súper", "Súper.md", hasLocalEdits = true),
        NotesFolderEntry.Note("Vacaciones", "Vacaciones.md", hasLocalEdits = false),
        NotesFolderEntry.File("luna.jpg", "luna.jpg", isImage = true, size = 250_000),
    ),
    sync: NotesSyncStatus = NotesSyncStatus.OK,
    showPrivacyNotice: Boolean = false,
    needChoiceCount: Int = 0,
    newNote: NewNoteDialog? = null,
    actionsFor: NotesFolderEntry? = null,
    dialog: FolderDialog? = null,
    showNewMenu: Boolean = false,
    canDeleteFolder: Boolean = false,
) = NotesFolderState(
    title = title,
    entries = persistentListOf(*entries.toTypedArray()),
    isRefreshing = false,
    sync = sync,
    showPrivacyNotice = showPrivacyNotice,
    needChoiceCount = needChoiceCount,
    newNote = newNote,
    actionsFor = actionsFor,
    dialog = dialog,
    busyPath = null,
    openFile = null,
    showNewMenu = showNewMenu,
    canDeleteFolder = canDeleteFolder,
    isGone = false,
    eventSink = {},
)
