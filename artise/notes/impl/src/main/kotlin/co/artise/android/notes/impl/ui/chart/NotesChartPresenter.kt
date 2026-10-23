/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.share.DocumentSharePresenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * The chart's picture (the server's PNG at [PICTURE_SIZE] for this screen's light or dark, the kept one at once) and its
 * data (`GET chart`, for the caption and "as of"), asked for each time it opens, on pull to refresh (after a sync) and
 * when the notes change: a chart that reads the chat's records is drawn as of now.
 */
@AssistedInject
class NotesChartPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val path: String,
    private val repository: NotesRepository,
    sharePresenterFactory: DocumentSharePresenter.Factory,
) : Presenter<NotesChartState> {
    @AssistedFactory
    fun interface Factory {
        fun create(roomId: RoomId, path: String): NotesChartPresenter
    }

    private val sharePresenter = sharePresenterFactory.create(roomId, path)

    @Composable
    override fun present(): NotesChartState {
        var fileTitle by remember { mutableStateOf<String?>(null) }
        var canEdit by remember { mutableStateOf(false) }
        var chart by remember { mutableStateOf<NotesChart?>(null) }
        var picture by remember { mutableStateOf<NotesPreview?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var failed by remember { mutableStateOf(false) }
        // 0: opened; each refresh adds one and syncs first.
        var attempts by remember { mutableIntStateOf(0) }
        var refreshes by remember { mutableIntStateOf(0) }
        val share = sharePresenter.present()
        val dark = !ElementTheme.isLightTheme

        LaunchedEffect(Unit) {
            // Saved in the editor, or changed by someone else and pulled by a sync: drawn again.
            repository.changes(roomId).collect { attempts++ }
        }
        LaunchedEffect(attempts, refreshes, dark) {
            isLoading = true
            if (refreshes > 0) repository.sync(roomId)
            val file = repository.file(roomId, path)
            fileTitle = file?.title?.takeIf { it.isNotBlank() }
            canEdit = file?.isReadOnly != true
            if (picture == null) {
                repository.cachedPreview(roomId, path, PICTURE_SIZE, dark, NoteAttachment.Kind.CHART)?.let { picture = it }
            }
            picture = repository.preview(roomId, path, PICTURE_SIZE, dark, NoteAttachment.Kind.CHART)
            val loaded = repository.chart(roomId, path, dark)
            loaded.onSuccess { chart = it }
            failed = loaded.isFailure
            isLoading = false
        }

        return NotesChartState(
            title = chart?.spec?.title?.takeIf { it.isNotBlank() } ?: fileTitle ?: path.substringAfterLast('/').removeSuffix(".chart"),
            chart = chart,
            picture = picture,
            isLoading = isLoading && picture == null && chart == null,
            isRefreshing = isLoading && refreshes > 0,
            failed = failed && !isLoading,
            canEdit = canEdit,
            share = share,
            eventSink = { event ->
                when (event) {
                    NotesChartEvent.Refresh -> refreshes++
                }
            },
        )
    }

    companion object {
        const val PICTURE_SIZE = 1024
    }
}
