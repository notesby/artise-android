/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.chats.toSyncStatus
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Shows a folder from the phone's copy at once, then syncs the chat and shows the result. */
@AssistedInject
class NotesFolderPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val folder: String,
    private val repository: NotesRepository,
) : Presenter<NotesFolderState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, folder: String): NotesFolderPresenter
    }

    @Composable
    override fun present(): NotesFolderState {
        val scope = rememberCoroutineScope()
        var title by remember { mutableStateOf(folder.substringAfterLast('/')) }
        var entries by remember { mutableStateOf<ImmutableList<NotesFolderEntry>>(persistentListOf()) }
        var isRefreshing by remember { mutableStateOf(true) }
        var sync by remember { mutableStateOf(NotesSyncStatus.OK) }
        var showPrivacyNotice by remember { mutableStateOf(false) }
        var refreshRequests by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            // The notice belongs to the top of a chat's notes, once per account.
            showPrivacyNotice = folder.isEmpty() && !repository.hasSeenPrivacyNotice()
            if (folder.isEmpty()) {
                title = repository.cachedChats().firstOrNull { it.roomId == roomId }?.name.orEmpty()
            }
        }

        LaunchedEffect(refreshRequests) {
            entries = NotesFolderEntries.of(repository.files(roomId), folder).toImmutableList()
            isRefreshing = true
            sync = repository.sync(roomId).fold(onSuccess = { NotesSyncStatus.OK }, onFailure = { it.toSyncStatus() })
            entries = NotesFolderEntries.of(repository.files(roomId), folder).toImmutableList()
            isRefreshing = false
        }

        return NotesFolderState(
            title = title,
            entries = entries,
            isRefreshing = isRefreshing,
            sync = sync,
            showPrivacyNotice = showPrivacyNotice,
            eventSink = { event ->
                when (event) {
                    NotesFolderEvent.Refresh -> refreshRequests++
                    NotesFolderEvent.DismissPrivacyNotice -> {
                        showPrivacyNotice = false
                        scope.launch { repository.markPrivacyNoticeSeen() }
                    }
                }
            },
        )
    }
}
