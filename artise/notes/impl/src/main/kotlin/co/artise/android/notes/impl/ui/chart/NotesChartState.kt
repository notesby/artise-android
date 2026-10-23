/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chart

import androidx.compose.runtime.Immutable
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.share.DocumentShareState

/** A chart of the chat's notes as the server draws it now, with Edit and Share. */
@Immutable
data class NotesChartState(
    /** The chart's title, or its file's name until it's loaded. */
    val title: String,
    /** Its data, for the caption and "as of". */
    val chart: NotesChart?,
    /** The server's picture at 1024 px for this screen's light or dark; [NotesPreview.None] when there's none. */
    val picture: NotesPreview?,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    /** The chart couldn't be loaded now (offline, a mistake in the file, the server can't draw). */
    val failed: Boolean,
    /** Not offered for a page Ari rewrites each period. */
    val canEdit: Boolean,
    val share: DocumentShareState,
    val eventSink: (NotesChartEvent) -> Unit,
) {
    /** What a screen reader says for the picture: the title, then the caption. */
    val altText: String get() = listOfNotNull(title, chart?.spec?.caption?.takeIf { it.isNotBlank() }).joinToString(". ")
}

sealed interface NotesChartEvent {
    /** Pull to refresh: a sync, then the chart and its picture again (a chart that reads records changes). */
    data object Refresh : NotesChartEvent
}
