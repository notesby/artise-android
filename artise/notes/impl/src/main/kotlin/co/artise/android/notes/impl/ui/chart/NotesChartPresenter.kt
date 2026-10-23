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
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.impl.share.DocumentSharePresenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Asks the server for the chart (its data and its SVG for this screen's light or dark) each time it opens and on pull
 * to refresh: a chart that reads the chat's records is drawn as of now.
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
        var isLoading by remember { mutableStateOf(true) }
        var problem by remember { mutableStateOf<ChartProblem?>(null) }
        var attempts by remember { mutableIntStateOf(0) }
        val share = sharePresenter.present()
        val dark = !ElementTheme.isLightTheme

        LaunchedEffect(Unit) {
            val file = repository.file(roomId, path)
            fileTitle = file?.title?.takeIf { it.isNotBlank() }
            canEdit = file?.isReadOnly != true
        }
        LaunchedEffect(Unit) {
            // Saved in the editor, or changed by someone else and pulled by a sync: drawn again.
            repository.changes(roomId).collect { attempts++ }
        }
        LaunchedEffect(attempts, dark) {
            isLoading = true
            repository.chart(roomId, path, dark).fold(
                onSuccess = {
                    chart = it
                    problem = null
                },
                onFailure = { error ->
                    // A mistake in the file says what it is; anything else keeps what's shown, if anything, with a retry.
                    problem = if (error is NotesException.BadChart) {
                        ChartProblem.BadChart(error.message.orEmpty())
                    } else {
                        ChartProblem.LoadFailed
                    }
                },
            )
            isLoading = false
        }

        return NotesChartState(
            title = chart?.spec?.title?.takeIf { it.isNotBlank() } ?: fileTitle ?: path.substringAfterLast('/').removeSuffix(".chart"),
            chart = chart,
            isLoading = isLoading && chart == null,
            isRefreshing = isLoading && chart != null,
            problem = problem,
            canEdit = canEdit,
            share = share,
            eventSink = { event ->
                when (event) {
                    NotesChartEvent.Refresh -> attempts++
                }
            },
        )
    }
}
