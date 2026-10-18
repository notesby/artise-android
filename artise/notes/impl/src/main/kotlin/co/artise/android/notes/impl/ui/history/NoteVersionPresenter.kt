/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId

/** A note as it was at one version, read-only. */
data class NoteVersionState(
    val title: String,
    /** When that version was saved ("Today, 18:02"); empty when unknown. */
    val savedAt: String,
    /** The note's Markdown at that version; `null` while loading or when it couldn't be loaded. */
    val content: String?,
    val isLoading: Boolean,
    val failed: Boolean,
    val eventSink: (NoteVersionEvent) -> Unit,
)

sealed interface NoteVersionEvent {
    data object Retry : NoteVersionEvent
}

@AssistedInject
class NoteVersionPresenter(
    @Assisted private val version: Version,
    private val repository: NotesRepository,
) : Presenter<NoteVersionState> {
    /** The note [path] in [roomId] at [commit]; [savedAt] says when, for the title. */
    data class Version(val roomId: RoomId, val path: String, val commit: String, val savedAt: String)

    @AssistedFactory
    fun interface Factory {
        fun create(version: Version): NoteVersionPresenter
    }

    @Composable
    override fun present(): NoteVersionState {
        var attempt by remember { mutableIntStateOf(0) }
        var content by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var failed by remember { mutableStateOf(false) }

        LaunchedEffect(attempt) {
            isLoading = true
            failed = false
            repository.noteAt(version.roomId, version.path, version.commit).fold(
                onSuccess = { content = it.content },
                onFailure = { failed = true },
            )
            isLoading = false
        }

        return NoteVersionState(
            title = NotesFolderEntries.noteName(version.path),
            savedAt = version.savedAt,
            content = content,
            isLoading = isLoading,
            failed = failed,
            eventSink = { event ->
                when (event) {
                    NoteVersionEvent.Retry -> attempt++
                }
            },
        )
    }
}
