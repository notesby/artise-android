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
fun interface NoteNavigator {
    fun openNote(path: String)
}

/**
 * Shows a note from the phone's copy (downloading it first if it isn't there yet), and asks the server
 * which notes link to it.
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

    @Composable
    override fun present(): NoteState {
        val scope = rememberCoroutineScope()
        var file by remember { mutableStateOf<LocalFile?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var backlinks by remember { mutableStateOf<BacklinksState>(BacklinksState.Loading) }
        var missingNote by remember { mutableStateOf<String?>(null) }

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
            missingNote = missingNote,
            eventSink = { event ->
                when (event) {
                    is NoteEvent.OpenNoteLink -> scope.launch {
                        val paths = repository.files(roomId).map { it.path }
                        val target = NoteLinkResolver.resolve(event.target, paths)
                        if (target != null) navigator.openNote(target) else missingNote = event.target
                    }
                    NoteEvent.DismissMissingNote -> missingNote = null
                }
            },
        )
    }
}
