/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

/** Searches the chat's notes on the phone as the person types, so it works offline. */
@AssistedInject
class NotesSearchPresenter(
    @Assisted private val roomId: RoomId,
    private val repository: NotesRepository,
) : Presenter<NotesSearchState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId): NotesSearchPresenter
    }

    @Composable
    override fun present(): NotesSearchState {
        val query = rememberTextFieldState()
        var results by remember { mutableStateOf<ImmutableList<NotesSearchHit>>(persistentListOf()) }

        LaunchedEffect(Unit) {
            snapshotFlow { query.text.toString() }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collectLatest { text -> results = LocalNotesSearch.search(repository.files(roomId), text).toImmutableList() }
        }

        return NotesSearchState(query = query, results = results)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 200L
    }
}
