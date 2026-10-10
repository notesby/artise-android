/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.FileChange
import co.artise.android.notes.impl.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class NotesChangesStatePreviewParam : PreviewParameterProvider<NotesChangesState> {
    override val values: Sequence<NotesChangesState>
        get() = sequenceOf(
            aNotesChangesState(
                days = listOf(
                    HistoryDay(
                        "Today",
                        persistentListOf(
                            aHistoryEntry(
                                "c1",
                                "18:02",
                                ChangeLabel.Text(R.string.screen_notes_change_automation, listOf("Ana", "#3")),
                                "Weekly shopping list",
                                aHistoryFile("Súper.md", "Súper", FileChange.MODIFIED, HistoryFileTarget.NOTE),
                            ),
                            aHistoryEntry(
                                "c2",
                                "09:15",
                                ChangeLabel.Text(R.string.screen_notes_change_app, listOf("Luis")),
                                "app: rename Mole.md",
                                aHistoryFile("Recetas/Mole poblano.md", "Mole poblano", FileChange.RENAMED, HistoryFileTarget.NOTE, fromName = "Mole"),
                                aHistoryFile("Fotos/luna.jpg", "luna.jpg", FileChange.DELETED, HistoryFileTarget.NONE),
                            ),
                        ),
                    ),
                    HistoryDay(
                        "Yesterday",
                        persistentListOf(
                            aHistoryEntry("c3", "21:40", ChangeLabel.Text(R.string.screen_notes_change_sync), "edited outside chat"),
                            aHistoryEntry(
                                "c4",
                                "07:00",
                                ChangeLabel.Text(R.string.screen_notes_change_ari_own),
                                "Medicines log",
                                aHistoryFile("Medicinas.md", "Medicinas", FileChange.ADDED, HistoryFileTarget.NOTE),
                            ),
                        ),
                    ),
                ),
                hasMore = true,
                isLoadingMore = true,
            ),
            aNotesChangesState(isLoading = true),
            aNotesChangesState(),
            aNotesChangesState(failed = true),
        )
}

internal fun aNotesChangesState(
    days: List<HistoryDay> = emptyList(),
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    failed: Boolean = false,
) = NotesChangesState(
    days = days.toImmutableList(),
    isLoading = isLoading,
    isLoadingMore = isLoadingMore,
    hasMore = hasMore,
    failed = failed,
    busyPath = null,
    openFile = null,
    fileProblem = null,
    eventSink = {},
)

internal fun aHistoryEntry(commit: String, time: String, label: ChangeLabel, message: String, vararg files: HistoryFile) = HistoryEntry(
    commit = commit,
    time = time,
    label = label,
    message = message,
    files = files.toList().toImmutableList(),
)

internal fun aHistoryFile(path: String, name: String, change: FileChange, target: HistoryFileTarget, fromName: String? = null) = HistoryFile(
    path = path,
    name = name,
    change = change,
    fromName = fromName,
    target = target,
)
