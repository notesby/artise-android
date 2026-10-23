/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chart

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.impl.share.DocumentShareState

/** A chart of the chat's notes as the server draws it now, with Edit and Share. */
@Immutable
data class NotesChartState(
    /** The chart's title, or its file's name until it's loaded. */
    val title: String,
    val chart: NotesChart?,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val problem: ChartProblem?,
    /** Not offered for a page Ari rewrites each period. */
    val canEdit: Boolean,
    val share: DocumentShareState,
    val eventSink: (NotesChartEvent) -> Unit,
) {
    /** What a screen reader says for the picture: the title, then the caption. */
    val altText: String get() = listOfNotNull(title, chart?.spec?.caption?.takeIf { it.isNotBlank() }).joinToString(". ")
}

@Immutable
sealed interface ChartProblem {
    /** Offline, or the server can't draw now. */
    data object LoadFailed : ChartProblem

    /** The file has a mistake: the server's sentence says what. */
    data class BadChart(val message: String) : ChartProblem
}

sealed interface NotesChartEvent {
    /** Pull to refresh, or try again: a chart that reads records changes while its file doesn't. */
    data object Refresh : NotesChartEvent
}
