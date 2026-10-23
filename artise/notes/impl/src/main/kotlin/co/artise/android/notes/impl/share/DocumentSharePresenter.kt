/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.api.NotesExport
import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.launch

@Immutable
data class DocumentShareState(
    /** The format being made right now, for the spinner; `null` when nothing is. */
    val exporting: ExportFormat?,
    /** Made and waiting to go to the share sheet. */
    val ready: NotesExport?,
    val problem: ShareProblem?,
    /** The person is in the document's chat, so Ari can be asked for a link there; `false` until known. */
    val canAskLink: Boolean,
    /** The request was posted in the chat: Ari answers there. */
    val linkAsked: Boolean,
    val eventSink: (DocumentShareEvent) -> Unit,
)

enum class ShareProblem {
    /** Couldn't be made: offline, or the server couldn't draw it now. */
    EXPORT_FAILED,
    LINK_FAILED,
}

sealed interface DocumentShareEvent {
    /** Makes the file now (never ahead: it's drawn on the server each time) under [name], for the share sheet. */
    data class Export(val format: ExportFormat, val name: String) : DocumentShareEvent

    /** The share sheet was shown with [DocumentShareState.ready]. */
    data object ExportHandled : DocumentShareEvent

    /** Posts [text] (in the app's language) in the document's chat, as the person, mentioning Ari. */
    data class AskForLink(val text: String, val mention: Mention?) : DocumentShareEvent

    /** Closes the problem or "link asked" dialog. */
    data object DismissDialog : DocumentShareEvent
}

/**
 * Share for a chart, a dashboard or a note: the server makes a PNG or PDF when Share is tapped, for the system share
 * sheet; a link is asked of Ari in the chat, since the app can't make one.
 */
@AssistedInject
class DocumentSharePresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
    private val messenger: ChatMessenger,
) : Presenter<DocumentShareState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): DocumentSharePresenter
    }

    @Composable
    override fun present(): DocumentShareState {
        val scope = rememberCoroutineScope()
        var exporting by remember { mutableStateOf<ExportFormat?>(null) }
        var ready by remember { mutableStateOf<NotesExport?>(null) }
        var problem by remember { mutableStateOf<ShareProblem?>(null) }
        var canAskLink by remember { mutableStateOf(false) }
        var linkAsked by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { canAskLink = messenger.canAsk(roomId) }

        fun handle(event: DocumentShareEvent) {
            when (event) {
                is DocumentShareEvent.Export -> if (exporting == null) {
                    exporting = event.format
                    scope.launch {
                        repository.export(roomId, path, event.format, event.name).fold(
                            onSuccess = { ready = it },
                            onFailure = { problem = ShareProblem.EXPORT_FAILED },
                        )
                        exporting = null
                    }
                }
                DocumentShareEvent.ExportHandled -> ready = null
                is DocumentShareEvent.AskForLink -> scope.launch {
                    messenger.send(roomId, event.text, event.mention).fold(
                        onSuccess = { linkAsked = true },
                        onFailure = { problem = ShareProblem.LINK_FAILED },
                    )
                }
                DocumentShareEvent.DismissDialog -> {
                    problem = null
                    linkAsked = false
                }
            }
        }

        return DocumentShareState(
            exporting = exporting,
            ready = ready,
            problem = problem,
            canAskLink = canAskLink,
            linkAsked = linkAsked,
            eventSink = ::handle,
        )
    }
}
