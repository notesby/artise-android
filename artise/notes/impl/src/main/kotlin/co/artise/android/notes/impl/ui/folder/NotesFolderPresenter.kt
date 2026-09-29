/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.chats.toSyncStatus
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NoteNames
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

fun interface NotesFolderNavigator {
    /** A note was just created: open it to write. */
    fun openEditor(path: String)
}

/**
 * Shows a folder from the phone's copy at once, syncs the chat, and follows later changes
 * (edits, choices, other people's changes pulled by a sync).
 */
@AssistedInject
class NotesFolderPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val folder: String,
    @Assisted private val navigator: NotesFolderNavigator,
    private val repository: NotesRepository,
) : Presenter<NotesFolderState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, folder: String, navigator: NotesFolderNavigator): NotesFolderPresenter
    }

    @Composable
    override fun present(): NotesFolderState {
        val scope = rememberCoroutineScope()
        var title by remember { mutableStateOf(folder.substringAfterLast('/')) }
        var entries by remember { mutableStateOf<ImmutableList<NotesFolderEntry>>(persistentListOf()) }
        var needChoiceCount by remember { mutableIntStateOf(0) }
        var isRefreshing by remember { mutableStateOf(true) }
        var sync by remember { mutableStateOf(NotesSyncStatus.OK) }
        var showPrivacyNotice by remember { mutableStateOf(false) }
        var newNote by remember { mutableStateOf<NewNoteDialog?>(null) }
        var refreshRequests by remember { mutableIntStateOf(0) }

        suspend fun reload() {
            entries = NotesFolderEntries.of(repository.files(roomId), folder).toImmutableList()
            needChoiceCount = repository.edits(roomId).count { it.state != EditState.PENDING }
        }

        LaunchedEffect(Unit) {
            // The notice belongs to the top of a chat's notes, once per account.
            showPrivacyNotice = folder.isEmpty() && !repository.hasSeenPrivacyNotice()
            if (folder.isEmpty()) {
                title = repository.cachedChats().firstOrNull { it.roomId == roomId }?.name.orEmpty()
            }
        }
        LaunchedEffect(Unit) {
            repository.changes(roomId).collect { reload() }
        }
        LaunchedEffect(refreshRequests) {
            reload()
            isRefreshing = true
            sync = repository.sync(roomId).fold(onSuccess = { NotesSyncStatus.OK }, onFailure = { it.toSyncStatus() })
            reload()
            isRefreshing = false
        }

        return NotesFolderState(
            title = title,
            entries = entries,
            isRefreshing = isRefreshing,
            sync = sync,
            showPrivacyNotice = showPrivacyNotice,
            needChoiceCount = needChoiceCount,
            newNote = newNote,
            eventSink = { event ->
                when (event) {
                    NotesFolderEvent.Refresh -> refreshRequests++
                    NotesFolderEvent.DismissPrivacyNotice -> {
                        showPrivacyNotice = false
                        scope.launch { repository.markPrivacyNoticeSeen() }
                    }
                    NotesFolderEvent.StartNewNote -> newNote = NewNoteDialog(problem = null)
                    NotesFolderEvent.CancelNewNote -> newNote = null
                    is NotesFolderEvent.CreateNote -> scope.launch {
                        val problem = NoteNames.problemWith(folder, event.name, repository.files(roomId).map { it.path })
                        if (problem != null) {
                            newNote = NewNoteDialog(problem)
                            return@launch
                        }
                        val path = NoteNames.pathFor(folder, event.name)
                        // Starts with the name as a title, so the note isn't blank on the server.
                        repository.createNote(roomId, path, "# ${event.name.trim()}\n\n")
                            .onSuccess {
                                newNote = null
                                repository.syncInBackground(roomId)
                                navigator.openEditor(path)
                            }
                            .onFailure { newNote = NewNoteDialog(problem = NoteNameProblem.EXISTS) }
                    }
                }
            },
        )
    }
}
