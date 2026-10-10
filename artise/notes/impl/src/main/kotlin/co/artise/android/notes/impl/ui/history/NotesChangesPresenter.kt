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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.ChangedFile
import co.artise.android.notes.api.FileChange
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NotesChange
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Loads a chat's changes page by page (the server's `more` and `before`), and opens the files they touched. */
@AssistedInject
class NotesChangesPresenter(
    @Assisted private val roomId: RoomId,
    private val repository: NotesRepository,
    private val dateFormatter: DateFormatter,
) : Presenter<NotesChangesState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId): NotesChangesPresenter
    }

    @Composable
    override fun present(): NotesChangesState {
        val scope = rememberCoroutineScope()
        var changes by remember { mutableStateOf<List<NotesChange>>(emptyList()) }
        var days by remember { mutableStateOf<ImmutableList<HistoryDay>>(persistentListOf()) }
        var hasMore by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var busyPath by remember { mutableStateOf<String?>(null) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }
        var fileProblem by remember { mutableStateOf<HistoryFileProblem?>(null) }

        suspend fun load(before: String?) {
            failed = false
            repository.changeHistory(roomId, before).fold(
                onSuccess = { page ->
                    // A change already listed (the page boundary moved) is shown once.
                    val known = changes.map { it.commit }.toSet()
                    changes = changes + page.changes.filter { it.commit !in known }
                    hasMore = page.more && page.changes.isNotEmpty()
                    val files = repository.files(roomId).associateBy { it.path }
                    days = NotesHistoryDates.groupByDay(changes, dateFormatter, at = { it.at }) { change, time -> change.toEntry(time, files) }
                },
                onFailure = { failed = true },
            )
        }

        fun loadMore() {
            if (isLoading || isLoadingMore || !hasMore) return
            isLoadingMore = true
            scope.launch {
                load(before = changes.lastOrNull()?.commit)
                isLoadingMore = false
            }
        }

        LaunchedEffect(Unit) {
            load(before = null)
            isLoading = false
        }

        return NotesChangesState(
            days = days,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            failed = failed,
            busyPath = busyPath,
            openFile = openFile,
            fileProblem = fileProblem,
            eventSink = { event ->
                when (event) {
                    NotesChangesEvent.LoadMore -> loadMore()
                    NotesChangesEvent.Retry -> if (changes.isEmpty()) {
                        isLoading = true
                        scope.launch {
                            load(before = null)
                            isLoading = false
                        }
                    } else {
                        hasMore = true
                        loadMore()
                    }
                    is NotesChangesEvent.OpenFile -> if (busyPath == null) {
                        busyPath = event.path
                        scope.launch {
                            repository.attachment(roomId, event.path).fold(
                                onSuccess = { openFile = OpenFileRequest(it.absolutePath, event.name) },
                                onFailure = { fileProblem = HistoryFileProblem.OFFLINE },
                            )
                            busyPath = null
                        }
                    }
                    is NotesChangesEvent.FileOpenHandled -> {
                        openFile = null
                        if (!event.opened) fileProblem = HistoryFileProblem.NO_APP
                    }
                    NotesChangesEvent.DismissFileProblem -> fileProblem = null
                }
            },
        )
    }

    private fun NotesChange.toEntry(time: String, files: Map<String, LocalFile>) = HistoryEntry(
        commit = commit,
        time = time,
        label = ChangeLabels.of(author),
        message = message,
        files = this.files.map { it.toHistoryFile(files) }.toImmutableList(),
    )

    companion object {
        /** What tapping [this] does, given the chat's files on the phone now. */
        internal fun ChangedFile.toHistoryFile(files: Map<String, LocalFile>): HistoryFile {
            val current = files[path]
            val isNote = current?.isNote ?: NotesHistoryDates.isNotePath(path)
            val target = when {
                change == FileChange.DELETED || current == null -> if (isNote) HistoryFileTarget.NOTE_HISTORY else HistoryFileTarget.NONE
                isNote -> HistoryFileTarget.NOTE
                else -> HistoryFileTarget.FILE
            }
            return HistoryFile(
                path = path,
                name = displayName(path, isNote),
                change = change,
                fromName = from?.let { displayName(it, isNote) },
                target = target,
            )
        }

        private fun displayName(path: String, isNote: Boolean) = if (isNote) NotesFolderEntries.noteName(path) else path.substringAfterLast('/')
    }
}
