/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Shows the chats with notes from the phone at once, then asks the server for the current list. */
@Inject
class NotesChatsPresenter(
    private val repository: NotesRepository,
) : Presenter<NotesChatsState> {
    @Composable
    override fun present(): NotesChatsState {
        var chats by remember { mutableStateOf<ImmutableList<NotesChat>>(persistentListOf()) }
        var isRefreshing by remember { mutableStateOf(true) }
        var sync by remember { mutableStateOf(NotesSyncStatus.OK) }
        var refreshRequests by remember { mutableIntStateOf(0) }

        LaunchedEffect(refreshRequests) {
            if (refreshRequests == 0) chats = repository.cachedChats().withNames()
            // Cached chats show at once; the spinner only when pulled down or when there's nothing to show yet.
            isRefreshing = refreshRequests > 0 || chats.isEmpty()
            repository.refreshChats()
                .onSuccess {
                    chats = it.withNames()
                    sync = NotesSyncStatus.OK
                }
                .onFailure { sync = it.toSyncStatus() }
            isRefreshing = false
        }

        return NotesChatsState(
            chats = chats,
            isRefreshing = isRefreshing,
            sync = sync,
            eventSink = { event ->
                when (event) {
                    NotesChatsEvent.Refresh -> refreshRequests++
                }
            },
        )
    }

    // A chat synced before the list was loaded has no name yet; it gets one on the next refresh.
    private fun List<NotesChat>.withNames() = filter { it.name.isNotEmpty() }.sortedBy { it.name.lowercase() }.toImmutableList()
}

internal fun Throwable.toSyncStatus(): NotesSyncStatus = if (this is NotesException.Network) NotesSyncStatus.OFFLINE else NotesSyncStatus.FAILED
