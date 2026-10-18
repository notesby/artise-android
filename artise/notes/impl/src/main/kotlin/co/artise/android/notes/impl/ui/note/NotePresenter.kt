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
import co.artise.android.notes.api.UploadStatus
import co.artise.android.notes.impl.markdown.ChecklistToggle
import co.artise.android.notes.impl.markdown.NoteLinkResolver
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NoteNames
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import java.io.File

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
        var embeds by remember { mutableStateOf<ImmutableMap<String, EmbedState>>(persistentMapOf()) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }
        var uploads by remember { mutableStateOf<Map<String, UploadStatus>>(emptyMap()) }

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
            // Photos and files added on this phone: waiting, uploading, retrying or refused.
            uploads = repository.uploads(roomId)
            repository.changes(roomId).collect { uploads = repository.uploads(roomId) }
        }
        LaunchedEffect(uploads) {
            // Once uploaded, a photo's waiting copy is gone: show the downloaded one instead.
            reloadMissingPhotos(embeds) { embeds = it }
        }
        LaunchedEffect(file?.content) {
            val content = file?.content ?: return@LaunchedEffect
            val files = repository.files(roomId).associateBy { it.path }
            val found = NoteEmbeds.resolve(content, files.keys)
            embeds = found.mapValues { (_, embedPath) ->
                val known = embeds.values.firstOrNull { it.path == embedPath }
                known ?: EmbedState(
                    path = embedPath,
                    name = NoteEmbeds.nameOf(embedPath),
                    isImage = NoteEmbeds.isImage(embedPath),
                    file = null,
                    failed = false,
                    size = files[embedPath]?.size?.takeIf { it > 0 },
                    isDownloading = false,
                    upload = null,
                )
            }.toImmutableMap()
            // Photos show inside the note: download each one not on the phone yet.
            for ((target, embed) in embeds) {
                if (!embed.isImage || embed.file != null) continue
                val result = repository.attachment(roomId, embed.path)
                embeds = (embeds + (target to embed.copy(file = result.getOrNull()?.absolutePath, failed = result.isFailure))).toImmutableMap()
            }
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

        fun markDownloading(attachmentPath: String, downloading: Boolean) {
            embeds = embeds.mapValues { (_, embed) -> if (embed.path == attachmentPath) embed.copy(isDownloading = downloading) else embed }
                .toImmutableMap()
        }

        suspend fun openAttachment(attachmentPath: String) {
            markDownloading(attachmentPath, true)
            val result = repository.attachment(roomId, attachmentPath)
            markDownloading(attachmentPath, false)
            result.fold(
                onSuccess = { downloaded -> openFile = OpenFileRequest(downloaded.absolutePath, NoteEmbeds.nameOf(attachmentPath)) },
                onFailure = { dialog = NoteDialog.AttachmentUnavailable },
            )
        }

        return NoteState(
            title = NotesFolderEntries.noteName(path),
            content = file?.content,
            isLoading = isLoading,
            hasLocalEdits = file?.hasLocalEdits == true,
            backlinks = backlinks,
            dialog = dialog,
            embeds = remember(embeds, uploads) { embeds.withUploads(uploads) },
            openFile = openFile,
            eventSink = { event ->
                when (event) {
                    is NoteEvent.OpenNoteLink -> scope.launch {
                        val paths = repository.files(roomId).map { it.path }
                        val target = NoteLinkResolver.resolve(event.target, paths)
                        when {
                            target == null -> dialog = NoteDialog.MissingNote(event.target)
                            // A link to a photo or file ("[[factura.pdf]]") opens it rather than a note screen.
                            !target.endsWith(".md") -> openAttachment(target)
                            else -> navigator.openNote(target)
                        }
                    }
                    is NoteEvent.ToggleTask -> scope.launch {
                        val content = file?.content ?: return@launch
                        val toggled = ChecklistToggle.toggle(content, event.lineIndex) ?: return@launch
                        // Shown at once, so a quick second tap starts from this tick rather than the old text.
                        file = file?.copy(content = toggled, hasLocalEdits = true)
                        repository.editNote(roomId, path, toggled)
                        repository.syncInBackground(roomId)
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
                    is NoteEvent.OpenAttachment -> scope.launch { openAttachment(event.path) }
                    is NoteEvent.RetryUpload -> scope.launch { repository.retryUpload(roomId, event.path) }
                    // The note's text loses the embed through the repository; the change arrives like any other.
                    is NoteEvent.CancelUpload -> scope.launch { repository.cancelUpload(roomId, event.path) }
                    is NoteEvent.FileOpenHandled -> {
                        openFile = null
                        if (!event.opened) dialog = NoteDialog.NoAppForFile
                    }
                    NoteEvent.DismissDialog -> dialog = null
                }
            },
        )
    }

    private suspend fun reloadMissingPhotos(embeds: ImmutableMap<String, EmbedState>, update: (ImmutableMap<String, EmbedState>) -> Unit) {
        var current = embeds
        for ((target, embed) in embeds.filterValues { it.file != null && !File(it.file).exists() }) {
            val result = repository.attachment(roomId, embed.path)
            current = (current + (target to embed.copy(file = result.getOrNull()?.absolutePath, failed = result.isFailure))).toImmutableMap()
            update(current)
        }
    }

    private fun Throwable.toRenameFailure() = when (this) {
        is NotesException.Network -> RenameFailure.OFFLINE
        // The engine refuses to move a note with unsent edits.
        is NotesException.Conflict -> if (current == null) RenameFailure.UNSENT_CHANGES else RenameFailure.OTHER
        else -> RenameFailure.OTHER
    }
}
