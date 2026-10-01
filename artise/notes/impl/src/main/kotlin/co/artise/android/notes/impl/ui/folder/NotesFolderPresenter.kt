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
import co.artise.android.notes.api.MediaInUseException
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.chats.toSyncStatus
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NoteNames
import co.artise.android.notes.impl.ui.note.OpenFileRequest
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
        var actionsFor by remember { mutableStateOf<NotesFolderEntry?>(null) }
        var dialog by remember { mutableStateOf<FolderDialog?>(null) }
        var busyPath by remember { mutableStateOf<String?>(null) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }
        // The note or file the open dialog is about (kept while moving from one dialog to the next).
        var target by remember { mutableStateOf<NotesFolderEntry?>(null) }
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
            // The phone's copy is already on screen: update quietly, with the spinner only when the person pulled
            // down or there's nothing to show yet.
            isRefreshing = refreshRequests > 0 || entries.isEmpty()
            sync = repository.sync(roomId).fold(
                onSuccess = { report -> if (report.failed > 0) NotesSyncStatus.PARTIAL else NotesSyncStatus.OK },
                onFailure = { it.toSyncStatus() },
            )
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
            actionsFor = actionsFor,
            dialog = dialog,
            busyPath = busyPath,
            openFile = openFile,
            eventSink = { event ->
                when (event) {
                    NotesFolderEvent.Refresh -> refreshRequests++
                    NotesFolderEvent.DismissPrivacyNotice -> {
                        showPrivacyNotice = false
                        scope.launch { repository.markPrivacyNoticeSeen() }
                    }
                    NotesFolderEvent.StartNewNote -> newNote = NewNoteDialog(problem = null)
                    NotesFolderEvent.CancelNewNote -> newNote = null
                    is NotesFolderEvent.ShowActions -> actionsFor = event.entry
                    NotesFolderEvent.DismissActions -> actionsFor = null
                    is NotesFolderEvent.OpenFile -> scope.launch {
                        actionsFor = null
                        busyPath = event.entry.path
                        repository.attachment(roomId, event.entry.path).fold(
                            onSuccess = { openFile = OpenFileRequest(it.absolutePath, event.entry.name) },
                            onFailure = { dialog = FolderDialog.Problem(FileProblem.OFFLINE) },
                        )
                        busyPath = null
                    }
                    is NotesFolderEvent.FileOpenHandled -> {
                        openFile = null
                        if (!event.opened) dialog = FolderDialog.Problem(FileProblem.NO_APP)
                    }
                    NotesFolderEvent.StartRename -> actionsFor?.let {
                        target = it
                        actionsFor = null
                        dialog = FolderDialog.Rename(it, problem = null)
                    }
                    is NotesFolderEvent.Rename -> target?.let { entry ->
                        scope.launch {
                            val problem = NoteNames.renameProblem(entry.path, event.newName, repository.files(roomId).map { it.path })
                            if (problem != null) {
                                dialog = FolderDialog.Rename(entry, problem)
                                return@launch
                            }
                            dialog = null
                            move(entry, NoteNames.renamedPath(entry.path, event.newName), onDone = { dialog = it }, onBusy = { busyPath = it })
                        }
                    }
                    NotesFolderEvent.StartMove -> actionsFor?.let { entry ->
                        target = entry
                        actionsFor = null
                        scope.launch {
                            val folders = NotesFolderEntries.allFolders(repository.files(roomId))
                            dialog = FolderDialog.MoveTo(entry, folders.toImmutableList(), current = entry.path.substringBeforeLast('/', ""))
                        }
                    }
                    is NotesFolderEvent.MoveTo -> target?.let { entry ->
                        dialog = null
                        scope.launch { move(entry, NoteNames.movedPath(entry.path, event.folder), onDone = { dialog = it }, onBusy = { busyPath = it }) }
                    }
                    NotesFolderEvent.StartNewFolder -> target?.let { dialog = FolderDialog.NewFolder(it, problem = null) }
                    is NotesFolderEvent.MoveToNewFolder -> target?.let { entry ->
                        val problem = NoteNames.folderProblem(event.name)
                        if (problem != null) {
                            dialog = FolderDialog.NewFolder(entry, problem)
                        } else {
                            dialog = null
                            // Moving a file into a folder that doesn't exist yet creates it.
                            scope.launch {
                                move(entry, NoteNames.movedPath(entry.path, NoteNames.folderPath(event.name)), onDone = { dialog = it }, onBusy = {
                                    busyPath =
                                    it
                                })
                            }
                        }
                    }
                    NotesFolderEvent.StartDelete -> actionsFor?.let {
                        target = it
                        actionsFor = null
                        dialog = FolderDialog.ConfirmDelete(it)
                    }
                    NotesFolderEvent.ConfirmDelete -> target?.let { entry ->
                        dialog = null
                        scope.launch { delete(entry, removeFromNotes = false, onDone = { dialog = it }, onBusy = { busyPath = it }) }
                    }
                    NotesFolderEvent.ConfirmRemoveAndDelete -> target?.let { entry ->
                        dialog = null
                        scope.launch { delete(entry, removeFromNotes = true, onDone = { dialog = it }, onBusy = { busyPath = it }) }
                    }
                    NotesFolderEvent.DismissDialog -> dialog = null
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

    /** Moves or renames [entry] to [to] on the server (it updates the links to it); a problem comes back as a dialog. */
    private suspend fun move(entry: NotesFolderEntry, to: String, onDone: (FolderDialog?) -> Unit, onBusy: (String?) -> Unit) {
        if (to == entry.path) return
        onBusy(entry.path)
        val result = repository.moveNote(roomId, entry.path, to)
        onBusy(null)
        onDone(result.exceptionOrNull()?.let { FolderDialog.Problem(it.toFileProblem()) })
    }

    /**
     * Deletes [entry]. A note waits in the queue like any edit; a photo or file is checked with the server first, and
     * one that notes use is only deleted after the person agrees to take it out of them.
     */
    private suspend fun delete(entry: NotesFolderEntry, removeFromNotes: Boolean, onDone: (FolderDialog?) -> Unit, onBusy: (String?) -> Unit) {
        if (entry !is NotesFolderEntry.File) {
            repository.deleteFile(roomId, entry.path)
            repository.syncInBackground(roomId)
            onDone(null)
            return
        }
        onBusy(entry.path)
        val result = repository.deleteMedia(roomId, entry.path, removeFromNotes)
        onBusy(null)
        onDone(
            when (val error = result.exceptionOrNull()) {
                null -> null
                is MediaInUseException -> FolderDialog.FileInUse(entry, error.usedBy.toImmutableList())
                else -> FolderDialog.Problem(error.toFileProblem())
            }
        )
    }

    private fun Throwable.toFileProblem(): FileProblem = when (this) {
        is NotesException.Network -> FileProblem.OFFLINE
        is NotesException.Exists -> FileProblem.EXISTS
        // The engine refuses to move a file with unsent changes, or one still uploading.
        is NotesException.Conflict -> if (current == null) FileProblem.UNSENT else FileProblem.FAILED
        else -> FileProblem.FAILED
    }
}
