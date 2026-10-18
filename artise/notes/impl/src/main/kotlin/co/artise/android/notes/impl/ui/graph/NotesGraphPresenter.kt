/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

data class NotesGraphState(
    /** `null` while the map is being laid out. */
    val graph: NotesGraphModel?,
)

/** Lays out the chat's notes and their links, and again whenever the notes change. */
@AssistedInject
class NotesGraphPresenter(
    @Assisted private val roomId: RoomId,
    private val repository: NotesRepository,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<NotesGraphState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId): NotesGraphPresenter
    }

    @Composable
    override fun present(): NotesGraphState {
        var graph by remember { mutableStateOf<NotesGraphModel?>(null) }
        LaunchedEffect(Unit) {
            repository.changes(roomId).onStart { emit(Unit) }.collect {
                val files = repository.files(roomId)
                // The layout is a few hundred passes over every pair of notes: keep it off the main thread.
                graph = withContext(dispatchers.computation) { NotesGraphBuilder.build(files) }
            }
        }
        return NotesGraphState(graph = graph)
    }
}
