/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.document

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Downloads the file through the Notes API (after a quick sync, so a changed file comes in its current version) and
 * shows it; the copy stays on the phone for next time.
 */
@AssistedInject
class NotesDocumentPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
) : Presenter<NotesDocumentState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): NotesDocumentPresenter
    }

    @Composable
    override fun present(): NotesDocumentState {
        var file by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var problem by remember { mutableStateOf<DocumentProblem?>(null) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }
        var attempts by remember { mutableIntStateOf(0) }
        val name = path.substringAfterLast('/')

        LaunchedEffect(attempts) {
            isLoading = true
            problem = null
            // Best effort: offline, the phone's copy (if any) is shown as it is.
            repository.sync(roomId)
            repository.attachment(roomId, path).fold(
                onSuccess = { file = it.absolutePath },
                onFailure = { problem = if (it is NotesException.Network) DocumentProblem.OFFLINE else DocumentProblem.FAILED },
            )
            isLoading = false
        }

        return NotesDocumentState(
            title = name,
            kind = NoteAttachment.kindOf(path),
            file = file,
            isLoading = isLoading,
            problem = problem,
            openFile = openFile,
            eventSink = { event ->
                when (event) {
                    NotesDocumentEvent.Retry -> attempts++
                    NotesDocumentEvent.OpenWith -> file?.let { openFile = OpenFileRequest(it, name) }
                    is NotesDocumentEvent.FileOpenHandled -> {
                        openFile = null
                        if (!event.opened) problem = DocumentProblem.NO_APP
                    }
                    NotesDocumentEvent.DismissProblem -> problem = null
                }
            },
        )
    }
}
