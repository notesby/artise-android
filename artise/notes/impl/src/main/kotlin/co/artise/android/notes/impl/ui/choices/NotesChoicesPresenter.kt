/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.choices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/** Lists the edits waiting for a choice in one chat, and applies the person's choice, then sends it. */
@AssistedInject
class NotesChoicesPresenter(
    @Assisted private val roomId: RoomId,
    private val repository: NotesRepository,
) : Presenter<NotesChoicesState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId): NotesChoicesPresenter
    }

    @Composable
    override fun present(): NotesChoicesState {
        val scope = rememberCoroutineScope()
        var items by remember { mutableStateOf<ImmutableList<NotesChoiceItem>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            repository.changes(roomId).onStart { emit(Unit) }.collect {
                items = repository.edits(roomId).filter { it.state != EditState.PENDING }.map { it.toItem() }.toImmutableList()
                isLoading = false
            }
        }

        fun choose(action: suspend () -> Unit) = scope.launch {
            action()
            repository.syncInBackground(roomId)
        }

        return NotesChoicesState(
            items = items,
            isLoading = isLoading,
            eventSink = { event ->
                when (event) {
                    is NotesChoicesEvent.KeepMine -> choose {
                        val mine = items.firstOrNull { it.editId == event.editId }?.mine ?: return@choose
                        repository.resolveConflict(event.editId, mine)
                    }
                    is NotesChoicesEvent.KeepMyCopy -> choose { repository.keepDeletedNote(event.editId) }
                    is NotesChoicesEvent.KeepBoth -> choose {
                        val item = items.firstOrNull { it.editId == event.editId } ?: return@choose
                        val taken = repository.files(roomId).map { it.path }.toSet()
                        repository.saveUnderNewName(event.editId, freeName(item.path, taken))
                    }
                    is NotesChoicesEvent.Discard -> choose { repository.discardEdit(event.editId) }
                }
            },
        )
    }

    private fun PendingEdit.toItem() = NotesChoiceItem(
        editId = id,
        path = path,
        name = NotesFolderEntries.noteName(path),
        kind = kind,
        state = state,
        mine = content,
        theirs = serverCopy?.content,
        error = error,
    )

    companion object {
        /** "Súper.md" → "Súper (2).md", or the next number free, so both notes can stay. */
        fun freeName(path: String, taken: Set<String>): String {
            val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
            val file = path.substringAfterLast('/')
            val base = file.substringBeforeLast('.')
            val extension = file.substringAfterLast('.', missingDelimiterValue = "")
            return generateSequence(2) { it + 1 }
                .map { number ->
                    val name = "$base ($number)" + if (extension.isEmpty()) "" else ".$extension"
                    if (folder.isEmpty()) name else "$folder/$name"
                }
                .first { it !in taken }
        }
    }
}
