/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A note's versions, newest first, grouped by day. */
data class NoteHistoryState(
    val title: String,
    val days: ImmutableList<HistoryDay>,
    val isLoading: Boolean,
    val failed: Boolean,
    val eventSink: (NoteHistoryEvent) -> Unit,
) {
    /** When the version [commit] was saved, for the version screen's title ("Sunday 6 April, 18:02"). */
    fun whenOf(commit: String): String? = days.firstNotNullOfOrNull { day ->
        day.entries.firstOrNull { it.commit == commit }?.let { listOf(day.label, it.time).filter(String::isNotEmpty).joinToString(", ") }
    }
}

sealed interface NoteHistoryEvent {
    data object Retry : NoteHistoryEvent
}

@AssistedInject
class NoteHistoryPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
    private val dateFormatter: DateFormatter,
) : Presenter<NoteHistoryState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): NoteHistoryPresenter
    }

    @Composable
    override fun present(): NoteHistoryState {
        var attempt by remember { mutableIntStateOf(0) }
        var days by remember { mutableStateOf<ImmutableList<HistoryDay>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }
        var failed by remember { mutableStateOf(false) }

        LaunchedEffect(attempt) {
            isLoading = true
            failed = false
            repository.history(roomId, path).fold(
                onSuccess = { versions ->
                    days = NotesHistoryDates.groupByDay(versions, dateFormatter, at = { it.at }) { version, time ->
                        HistoryEntry(
                            commit = version.commit,
                            time = time,
                            label = ChangeLabels.of(version.author),
                            message = version.message,
                            files = persistentListOf(),
                        )
                    }
                },
                onFailure = { failed = true },
            )
            isLoading = false
        }

        return NoteHistoryState(
            title = NotesFolderEntries.noteName(path),
            days = days,
            isLoading = isLoading,
            failed = failed,
            eventSink = { event ->
                when (event) {
                    NoteHistoryEvent.Retry -> attempt++
                }
            },
        )
    }
}
