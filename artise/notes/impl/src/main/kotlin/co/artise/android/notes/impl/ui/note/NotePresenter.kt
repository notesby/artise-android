/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.markdown.NoteLinkResolver
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NoteNames
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Where a note screen can send the person. */
interface NoteNavigator {
    fun openNote(path: String)

    fun openEditor(path: String)

    /** The note was renamed: show it under its new path. */
    fun onRenamed(newPath: String)

    /** The note was deleted: leave its screen. */
    fun onDeleted()
}

/**
 * Shows a note from the phone's copy (downloading it first if it isn't there yet), follows later changes,
 * asks the server which notes link to it, and handles create-from-link, rename and delete.
 */
@AssistedInject
class NotePresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    @Assisted private val navigator: NoteNavigator,
    private val repository: NotesRepository,
) : Presenter<NoteState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String, navigator: NoteNavigator): NotePresenter
    }

    private val folder = path.substringBeforeLast('/', missingDelimiterValue = "")

    @Composable
    override fun present(): NoteState {
        val scope = rememberCoroutineScope()
        var file by remember { mutableStateOf<LocalFile?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var backlinks by remember { mutableStateOf<BacklinksState>(BacklinksState.Loading) }
        var dialog by remember { mutableStateOf<NoteDialog?>(null) }

        LaunchedEffect(Unit) {
            file = repository.file(roomId, path)
            if (file?.content == null) {
                // Not downloaded yet: a sync fetches it, when there's a connection.
                repository.sync(roomId)
                file = repository.file(roomId, path)
            }
            isLoading = false
        }
        LaunchedEffect(Unit) {
            // An edit saved in the editor, a choice made, or someone else's change pulled by a sync.
            repository.changes(roomId).collect { file = repository.file(roomId, path) }
        }
        LaunchedEffect(Unit) {
            backlinks = repository.links(roomId, path).fold(
                onSuccess = { links -> BacklinksState.Loaded(links.backlinks.toImmutableList()) },
                onFailure = { error ->
                    // A note created on this phone isn't on the server yet: nothing links to it.
                    if (error is NotesException.NotFound) BacklinksState.Loaded(persistentListOf()) else BacklinksState.Offline
                },
            )
        }

        return NoteState(
            title = NotesFolderEntries.noteName(path),
            content = file?.content,
            isLoading = isLoading,
            hasLocalEdits = file?.hasLocalEdits == true,
            backlinks = backlinks,
            dialog = dialog,
            eventSink = { event ->
                when (event) {
                    is NoteEvent.OpenNoteLink -> scope.launch {
                        val paths = repository.files(roomId).map { it.path }
                        val target = NoteLinkResolver.resolve(event.target, paths)
                        if (target != null) navigator.openNote(target) else dialog = NoteDialog.MissingNote(event.target)
                    }
                    NoteEvent.CreateMissingNote -> scope.launch {
                        val target = (dialog as? NoteDialog.MissingNote)?.target ?: return@launch
                        dialog = null
                        // "[[Recetas/Mole]]" names its folder; a bare name goes next to this note, as Obsidian does.
                        val newPath = if (target.contains('/')) NoteNames.pathFor("", target) else NoteNames.pathFor(folder, target)
                        val name = newPath.substringAfterLast('/').removeSuffix(".md")
                        repository.createNote(roomId, newPath, "# $name\n\n").onSuccess {
                            repository.syncInBackground(roomId)
                            navigator.openEditor(newPath)
                        }
                    }
                    NoteEvent.StartRename -> dialog = NoteDialog.Rename(NotesFolderEntries.noteName(path), problem = null)
                    is NoteEvent.Rename -> scope.launch {
                        val others = repository.files(roomId).map { it.path }.filter { it != path }
                        val problem = NoteNames.problemWith(folder, event.newName, others)
                        if (problem != null) {
                            dialog = NoteDialog.Rename(event.newName, problem)
                            return@launch
                        }
                        val newPath = NoteNames.pathFor(folder, event.newName)
                        if (newPath == path) {
                            dialog = null
                            return@launch
                        }
                        repository.moveNote(roomId, path, newPath).fold(
                            onSuccess = {
                                dialog = null
                                navigator.onRenamed(newPath)
                            },
                            onFailure = { error ->
                                dialog = if (error is NotesException.Exists) {
                                    // Someone else took the name meanwhile.
                                    NoteDialog.Rename(event.newName, NoteNameProblem.EXISTS)
                                } else {
                                    NoteDialog.RenameFailed(error.toRenameFailure())
                                }
                            },
                        )
                    }
                    NoteEvent.StartDelete -> dialog = NoteDialog.ConfirmDelete
                    NoteEvent.ConfirmDelete -> scope.launch {
                        dialog = null
                        repository.deleteFile(roomId, path)
                        repository.syncInBackground(roomId)
                        navigator.onDeleted()
                    }
                    NoteEvent.DismissDialog -> dialog = null
                }
            },
        )
    }

    private fun Throwable.toRenameFailure() = when (this) {
        is NotesException.Network -> RenameFailure.OFFLINE
        // The engine refuses to move a note with unsent edits.
        is NotesException.Conflict -> if (current == null) RenameFailure.UNSENT_CHANGES else RenameFailure.OTHER
        else -> RenameFailure.OTHER
    }
}
