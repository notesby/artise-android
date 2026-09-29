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
        )
}

fun aNotesFolderState(
    title: String = "Familia",
    entries: List<NotesFolderEntry> = listOf(
        NotesFolderEntry.Folder("Recetas", "Recetas", 12),
        NotesFolderEntry.Note("Súper", "Súper.md", hasLocalEdits = true),
        NotesFolderEntry.Note("Vacaciones", "Vacaciones.md", hasLocalEdits = false),
    ),
    sync: NotesSyncStatus = NotesSyncStatus.OK,
    showPrivacyNotice: Boolean = false,
    needChoiceCount: Int = 0,
    newNote: NewNoteDialog? = null,
) = NotesFolderState(
    title = title,
    entries = persistentListOf(*entries.toTypedArray()),
    isRefreshing = false,
    sync = sync,
    showPrivacyNotice = showPrivacyNotice,
    needChoiceCount = needChoiceCount,
    newNote = newNote,
    eventSink = {},
)
