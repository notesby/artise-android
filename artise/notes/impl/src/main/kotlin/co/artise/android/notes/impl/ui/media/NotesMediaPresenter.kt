/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.MediaFile
import co.artise.android.notes.api.MediaInUseException
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.note.NoteEmbeds
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

/**
 * A chat's photos and files, and which notes use each one. Deleting is careful: it needs a connection so the server
 * can say which notes use the file, and a file in use is only deleted after the person agrees to take it out of those
 * notes.
 */
@AssistedInject
class NotesMediaPresenter(
    @Assisted private val roomId: RoomId,
    private val repository: NotesRepository,
) : Presenter<NotesMediaState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId): NotesMediaPresenter
    }

    @Composable
    override fun present(): NotesMediaState {
        val scope = rememberCoroutineScope()
        var all by remember { mutableStateOf<ImmutableList<MediaItem>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }
        var filter by remember { mutableStateOf(MediaFilter.ALL) }
        var deleting by remember { mutableStateOf<String?>(null) }
        var dialog by remember { mutableStateOf<MediaDialog?>(null) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }

        suspend fun reload() {
            all = repository.media(roomId)
                // Unused first, then the biggest: what's worth cleaning up comes to the top.
                .sortedWith(compareBy<MediaFile> { it.usedBy.isNotEmpty() }.thenByDescending { it.size })
                .map { media ->
                    MediaItem(
                        path = media.path,
                        name = NoteEmbeds.nameOf(media.path),
                        isImage = NoteEmbeds.isImage(media.path),
                        size = media.size,
                        usedBy = media.usedBy.toImmutableList(),
                        upload = media.upload,
                        file = repository.cachedAttachment(roomId, media.path)?.absolutePath,
                    )
                }
                .toImmutableList()
            isLoading = false
        }

        LaunchedEffect(Unit) {
            reload()
            repository.changes(roomId).collect { reload() }
        }

        /** Deletes [item]; notes that turn out to use it (the server knows best) are shown before anything happens. */
        suspend fun delete(item: MediaItem, removeFromNotes: Boolean): Boolean {
            deleting = item.path
            val result = repository.deleteMedia(roomId, item.path, removeFromNotes)
            deleting = null
            result.onFailure { error ->
                dialog = when (error) {
                    is MediaInUseException -> MediaDialog.ConfirmRemoveAndDelete(item, error.usedBy.toImmutableList())
                    is NotesException.Network -> MediaDialog.Offline
                    else -> MediaDialog.Failed
                }
            }
            reload()
            return result.isSuccess
        }

        val unused = all.filter { it.usedBy.isEmpty() && it.upload == null }
        return NotesMediaState(
            items = if (filter == MediaFilter.UNUSED) unused.toImmutableList() else all,
            filter = filter,
            unusedCount = unused.size,
            unusedBytes = unused.sumOf { it.size },
            isLoading = isLoading,
            deleting = deleting,
            dialog = dialog,
            openFile = openFile,
            eventSink = { event ->
                when (event) {
                    is NotesMediaEvent.SetFilter -> filter = event.filter
                    is NotesMediaEvent.Open -> scope.launch {
                        repository.attachment(roomId, event.item.path).fold(
                            onSuccess = { openFile = OpenFileRequest(it.absolutePath, event.item.name) },
                            onFailure = { dialog = MediaDialog.Offline },
                        )
                    }
                    is NotesMediaEvent.FileOpenHandled -> {
                        openFile = null
                        if (!event.opened) dialog = MediaDialog.NoAppForFile
                    }
                    is NotesMediaEvent.Delete -> dialog = if (event.item.usedBy.isEmpty()) {
                        MediaDialog.ConfirmDelete(event.item)
                    } else {
                        MediaDialog.ConfirmRemoveAndDelete(event.item, event.item.usedBy)
                    }
                    NotesMediaEvent.ConfirmDelete -> when (val confirming = dialog) {
                        is MediaDialog.ConfirmDelete -> {
                            dialog = null
                            scope.launch { delete(confirming.item, removeFromNotes = false) }
                        }
                        is MediaDialog.ConfirmRemoveAndDelete -> {
                            dialog = null
                            scope.launch { delete(confirming.item, removeFromNotes = true) }
                        }
                        else -> Unit
                    }
                    NotesMediaEvent.DeleteUnused -> if (unused.isNotEmpty()) {
                        dialog = MediaDialog.ConfirmDeleteUnused(unused.size, unused.sumOf { it.size })
                    }
                    NotesMediaEvent.ConfirmDeleteUnused -> {
                        dialog = null
                        scope.launch {
                            // One at a time, each checked with the server; one that turns out to be used stops here and asks.
                            for (item in unused) {
                                if (!delete(item, removeFromNotes = false)) break
                            }
                        }
                    }
                    is NotesMediaEvent.RetryUpload -> scope.launch { repository.retryUpload(roomId, event.item.path) }
                    NotesMediaEvent.DismissDialog -> dialog = null
                }
            },
        )
    }
}
