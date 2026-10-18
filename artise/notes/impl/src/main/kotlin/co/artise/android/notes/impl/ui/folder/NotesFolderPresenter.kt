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
        var showNewMenu by remember { mutableStateOf(false) }
        var isGone by remember { mutableStateOf(false) }

        suspend fun reload() {
            entries = NotesFolderEntries.of(repository.files(roomId), folder, repository.folders(roomId)).toImmutableList()
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
            showNewMenu = showNewMenu,
            canDeleteFolder = folder.isNotEmpty() && entries.isEmpty() && !isRefreshing,
            isGone = isGone,
            eventSink = { event ->
                when (event) {
                    NotesFolderEvent.Refresh -> refreshRequests++
                    NotesFolderEvent.DismissPrivacyNotice -> {
                        showPrivacyNotice = false
                        scope.launch { repository.markPrivacyNoticeSeen() }
                    }
                    NotesFolderEvent.StartNewNote -> {
                        showNewMenu = false
                        newNote = NewNoteDialog(problem = null)
                    }
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
                            val paths = repository.files(roomId).map { it.path }
                            val problem = if (entry is NotesFolderEntry.Folder) {
                                NoteNames.folderRenameProblem(entry.path, event.newName, paths + repository.folders(roomId))
                            } else {
                                NoteNames.renameProblem(entry.path, event.newName, paths)
                            }
                            if (problem != null) {
                                dialog = FolderDialog.Rename(entry, problem)
                                return@launch
                            }
                            dialog = null
                            val to = if (entry is NotesFolderEntry.Folder) {
                                NoteNames.movedPath(event.newName.trim(), entry.path.substringBeforeLast('/', ""))
                            } else {
                                NoteNames.renamedPath(entry.path, event.newName)
                            }
                            move(entry, to, onDone = { dialog = it }, onBusy = { busyPath = it })
                        }
                    }
                    NotesFolderEvent.StartMove -> actionsFor?.let { entry ->
                        target = entry
                        actionsFor = null
                        scope.launch {
                            // A folder can't go inside itself.
                            val folders = repository.folders(roomId).filterNot { entry is NotesFolderEntry.Folder && it.isInside(entry.path) }
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
                    NotesFolderEvent.StartDelete -> actionsFor?.let { entry ->
                        target = entry
                        actionsFor = null
                        if (entry is NotesFolderEntry.Folder) {
                            scope.launch { dialog = contentsOf(entry) }
                        } else {
                            dialog = FolderDialog.ConfirmDelete(entry)
                        }
                    }
                    NotesFolderEvent.ConfirmDelete -> target?.let { entry ->
                        val confirmed = dialog as? FolderDialog.ConfirmDeleteFolder
                        dialog = null
                        scope.launch {
                            if (entry is NotesFolderEntry.Folder) {
                                deleteFolder(entry, confirmed, onDone = { dialog = it }, onBusy = { busyPath = it })
                            } else {
                                delete(entry, removeFromNotes = false, onDone = { dialog = it }, onBusy = { busyPath = it })
                            }
                        }
                    }
                    NotesFolderEvent.ConfirmRemoveAndDelete -> target?.let { entry ->
                        dialog = null
                        scope.launch { delete(entry, removeFromNotes = true, onDone = { dialog = it }, onBusy = { busyPath = it }) }
                    }
                    NotesFolderEvent.DismissDialog -> dialog = null
                    NotesFolderEvent.ShowNewMenu -> showNewMenu = true
                    NotesFolderEvent.DismissNewMenu -> showNewMenu = false
                    NotesFolderEvent.StartCreateFolder -> {
                        showNewMenu = false
                        dialog = FolderDialog.CreateFolder(problem = null)
                    }
                    is NotesFolderEvent.CreateFolder -> scope.launch {
                        val problem = NoteNames.folderProblem(event.name)
                        if (problem != null) {
                            dialog = FolderDialog.CreateFolder(problem)
                            return@launch
                        }
                        val path = listOf(folder, NoteNames.folderPath(event.name)).filter { it.isNotEmpty() }.joinToString("/")
                        val taken = repository.folders(roomId) + repository.files(roomId).map { it.path }
                        if (taken.any { it.equals(path, ignoreCase = true) }) {
                            dialog = FolderDialog.CreateFolder(NoteNameProblem.EXISTS)
                            return@launch
                        }
                        dialog = null
                        busyPath = path
                        val result = repository.createFolder(roomId, path)
                        busyPath = null
                        result.exceptionOrNull()?.let { error ->
                            dialog = when (error) {
                                is NotesException.Exists -> FolderDialog.CreateFolder(NoteNameProblem.EXISTS)
                                else -> FolderDialog.Problem(error.toFileProblem())
                            }
                        }
                    }
                    NotesFolderEvent.DeleteThisFolder -> scope.launch {
                        // Only offered when the folder looks empty: the server refuses if something new arrived in it.
                        busyPath = folder
                        val result = repository.deleteFolder(roomId, folder, recursive = false)
                        busyPath = null
                        result.fold(
                            onSuccess = { isGone = true },
                            onFailure = { dialog = FolderDialog.Problem(it.toFileProblem()) },
                        )
                    }
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
        val error = if (entry is NotesFolderEntry.Folder) {
            repository.moveFolder(roomId, entry.path, to).exceptionOrNull()
        } else {
            repository.moveNote(roomId, entry.path, to).exceptionOrNull()
        }
        onBusy(null)
        onDone(error?.let { FolderDialog.Problem(it.toFileProblem()) })
    }

    /** What deleting [entry] takes with it, from the phone's copy. */
    private suspend fun contentsOf(entry: NotesFolderEntry.Folder): FolderDialog.ConfirmDeleteFolder {
        val inside = repository.files(roomId).filter { it.path.isInside(entry.path) }
        return FolderDialog.ConfirmDeleteFolder(
            entry = entry,
            notes = inside.count { it.isNote },
            files = inside.count { !it.isNote },
            folders = repository.folders(roomId).count { it.isInside(entry.path) && it != entry.path },
        )
    }

    /**
     * Deletes a folder the person confirmed, with what's inside ([confirmed] says what they were told). If the server
     * holds more than that, it refuses: sync and ask again with the new counts.
     */
    private suspend fun deleteFolder(
        entry: NotesFolderEntry.Folder,
        confirmed: FolderDialog.ConfirmDeleteFolder?,
        onDone: (FolderDialog?) -> Unit,
        onBusy: (String?) -> Unit,
    ) {
        val shown = contentsOf(entry)
        val recursive = listOfNotNull(shown, confirmed).any { it.notes + it.files + it.folders > 0 }
        onBusy(entry.path)
        val result = repository.deleteFolder(roomId, entry.path, recursive)
        val error = result.exceptionOrNull()
        if (error is NotesException.NotEmpty) {
            repository.sync(roomId)
            val updated = contentsOf(entry)
            onBusy(null)
            // The tree may still lag behind: then the server's own count says how much is inside.
            onDone(if (updated == shown) updated.copy(notes = 0, files = error.files, folders = error.folders) else updated)
            return
        }
        onBusy(null)
        onDone(error?.let { FolderDialog.Problem(it.toFileProblem()) })
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
        is NotesException.NotFound -> FileProblem.GONE
        // The engine refuses to move a file with unsent changes, or one still uploading.
        is NotesException.Conflict -> if (current == null) FileProblem.UNSENT else FileProblem.FAILED
        else -> FileProblem.FAILED
    }

    /** True for [folder] itself and anything at any depth inside it. */
    private fun String.isInside(folder: String) = this == folder || startsWith("$folder/")
}
