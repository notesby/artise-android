/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.share.DocumentSharePresenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId

/** Loads the dashboard's page from the server each time it opens, so its charts are as of now. */
@AssistedInject
class NotesDashboardPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
    sharePresenterFactory: DocumentSharePresenter.Factory,
) : Presenter<NotesDashboardState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): NotesDashboardPresenter
    }

    private val sharePresenter = sharePresenterFactory.create(roomId, path)

    @Composable
    override fun present(): NotesDashboardState {
        var title by remember { mutableStateOf(path.substringAfterLast('/').removeSuffix(".md")) }
        var canEdit by remember { mutableStateOf(false) }
        var html by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var problem by remember { mutableStateOf<DashboardProblem?>(null) }
        var attempts by remember { mutableIntStateOf(0) }
        val share = sharePresenter.present()

        LaunchedEffect(Unit) {
            val file = repository.file(roomId, path)
            file?.title?.takeIf { it.isNotBlank() }?.let { title = it }
            canEdit = file?.isReadOnly != true
        }
        LaunchedEffect(attempts) {
            isLoading = true
            problem = null
            repository.dashboard(roomId, path).fold(
                onSuccess = { html = it },
                onFailure = { error ->
                    problem = if (error is NotesException.Server && error.status == HTTP_NO_DASHBOARD) {
                        DashboardProblem.NOT_A_DASHBOARD
                    } else {
                        DashboardProblem.LOAD_FAILED
                    }
                },
            )
            isLoading = false
        }

        return NotesDashboardState(
            title = title,
            html = html,
            isLoading = isLoading,
            problem = problem,
            canEdit = canEdit,
            share = share,
            eventSink = { event ->
                when (event) {
                    NotesDashboardEvent.Retry -> attempts++
                }
            },
        )
    }

    private companion object {
        const val HTTP_NO_DASHBOARD = 415
    }
}
