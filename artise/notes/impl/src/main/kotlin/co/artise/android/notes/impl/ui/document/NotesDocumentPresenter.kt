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
import co.artise.android.notes.impl.ui.find.TextFinder
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads the file through the Notes API (after a quick sync, so a changed file comes in its current version) and
 * shows it; the copy stays on the phone for next time. A CSV shows as its text; a Word, Excel or PowerPoint file as the
 * server's PDF conversion, while "Open with" still hands over the original.
 */
@AssistedInject
class NotesDocumentPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<NotesDocumentState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): NotesDocumentPresenter
    }

    @Composable
    override fun present(): NotesDocumentState {
        val kind = remember { NoteAttachment.kindOf(path) }
        var file by remember { mutableStateOf<String?>(null) }
        var pdfFile by remember { mutableStateOf<String?>(null) }
        var tableText by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var problem by remember { mutableStateOf<DocumentProblem?>(null) }
        var openFile by remember { mutableStateOf<OpenFileRequest?>(null) }
        var attempts by remember { mutableIntStateOf(0) }
        // Finding: the text comes from the server once per version of the file, the first time the bar opens.
        var isFinding by remember { mutableStateOf(false) }
        var findQuery by remember { mutableStateOf("") }
        var findCurrent by remember { mutableIntStateOf(0) }
        var textPages by remember { mutableStateOf<ImmutableList<String>?>(null) }
        var isLoadingText by remember { mutableStateOf(false) }
        var textUnavailable by remember { mutableStateOf(false) }
        val findMatches = remember(findQuery, textPages) { TextFinder.find(findQuery, textPages.orEmpty()).toImmutableList() }
        val name = path.substringAfterLast('/')

        LaunchedEffect(attempts) {
            isLoading = true
            problem = null
            // Best effort: offline, the phone's copy (if any) is shown as it is.
            repository.sync(roomId)
            repository.attachment(roomId, path).fold(
                onSuccess = { downloaded ->
                    when (kind) {
                        NoteAttachment.Kind.PDF -> pdfFile = downloaded.absolutePath
                        // No conversion (an older server, or one it couldn't make): an icon and "Open with", as before.
                        NoteAttachment.Kind.OFFICE -> pdfFile = repository.officePdf(roomId, path).getOrNull()?.absolutePath
                        NoteAttachment.Kind.TABLE -> {
                            val text = withContext(dispatchers.io) { readTable(downloaded) }
                            tableText = text
                            // Found on the phone: no need for the server's text.
                            textPages = persistentListOf(text)
                        }
                        else -> Unit
                    }
                    file = downloaded.absolutePath
                },
                onFailure = { problem = if (it is NotesException.Network) DocumentProblem.OFFLINE else DocumentProblem.FAILED },
            )
            isLoading = false
        }
        LaunchedEffect(isFinding) {
            if (!isFinding || textPages != null || textUnavailable) return@LaunchedEffect
            isLoadingText = true
            repository.documentText(roomId, path).fold(
                onSuccess = { text -> textPages = text.pages.sortedBy { it.n }.map { it.text }.toImmutableList() },
                // Too big, a scanned PDF the server couldn't read, a kind with no text, or gone: search just isn't on offer.
                onFailure = { textUnavailable = true },
            )
            isLoadingText = false
        }

        return NotesDocumentState(
            title = name,
            kind = kind,
            file = file,
            pdfFile = pdfFile,
            tableText = tableText,
            isLoading = isLoading,
            problem = problem,
            openFile = openFile,
            find = if (isFinding) {
                DocumentFind(
                    query = findQuery,
                    pages = textPages ?: persistentListOf(),
                    matches = findMatches,
                    current = findCurrent.coerceIn(0, (findMatches.size - 1).coerceAtLeast(0)),
                    isLoading = isLoadingText,
                    isUnavailable = textUnavailable,
                )
            } else {
                null
            },
            eventSink = { event ->
                when (event) {
                    NotesDocumentEvent.Retry -> attempts++
                    NotesDocumentEvent.OpenFind -> {
                        isFinding = true
                        findCurrent = 0
                    }
                    is NotesDocumentEvent.FindQueryChanged -> {
                        findQuery = event.query
                        findCurrent = 0
                    }
                    NotesDocumentEvent.FindNext -> findCurrent = TextFinder.next(findCurrent, TextFinder.find(findQuery, textPages.orEmpty()).size)
                    NotesDocumentEvent.FindPrevious -> findCurrent = TextFinder.previous(findCurrent, TextFinder.find(findQuery, textPages.orEmpty()).size)
                    NotesDocumentEvent.CloseFind -> isFinding = false
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

/** A CSV's text, cut at [MAX_TABLE_CHARS] so a huge export can't stall the screen; "Open with" has the whole file. */
private fun readTable(file: File): String = file.bufferedReader().use { reader ->
    val buffer = CharArray(MAX_TABLE_CHARS)
    var read = 0
    while (read < MAX_TABLE_CHARS) {
        val n = reader.read(buffer, read, MAX_TABLE_CHARS - read)
        if (n < 0) break
        read += n
    }
    String(buffer, 0, read)
}

private const val MAX_TABLE_CHARS = 500_000
