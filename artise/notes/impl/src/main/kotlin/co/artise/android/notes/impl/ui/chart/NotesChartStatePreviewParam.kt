/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chart

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.ChartSpec
import co.artise.android.notes.api.NotesChart
import co.artise.android.notes.impl.share.ChatForLink
import co.artise.android.notes.impl.share.DocumentShareState

open class NotesChartStatePreviewParam : PreviewParameterProvider<NotesChartState> {
    override val values: Sequence<NotesChartState>
        get() = sequenceOf(
            aNotesChartState(),
            aNotesChartState(isLoading = false, chart = aNotesChart()),
            aNotesChartState(isLoading = false, problem = ChartProblem.LoadFailed),
            aNotesChartState(isLoading = false, problem = ChartProblem.BadChart("row 2, Ventas: 'mucho' isn't a number"), canEdit = false),
        )
}

internal fun aNotesChart() = NotesChart(
    path = "charts/ventas.chart",
    version = "3f2a",
    spec = ChartSpec(
        title = "Ventas por mes",
        type = "bar",
        columns = listOf("Mes", "Ventas"),
        rows = listOf(listOf("2026-05", "12000"), listOf("2026-06", "15400")),
        series = listOf("Ventas"),
        unit = "MXN",
        caption = "Sin IVA",
        colors = emptyList(),
        compare = "",
    ),
    asOf = "2026-10-22",
    svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"320\" height=\"180\"><rect x=\"40\" y=\"60\" width=\"80\" height=\"100\" " +
        "fill=\"#22408A\"/><rect x=\"180\" y=\"30\" width=\"80\" height=\"130\" fill=\"#22408A\"/></svg>",
)

internal fun aDocumentShareState() = DocumentShareState(
    exporting = null,
    ready = null,
    problem = null,
    chat = ChatForLink(canAsk = true, othersSee = true),
    linkAsked = false,
    eventSink = {},
)

internal fun aNotesChartState(
    chart: NotesChart? = null,
    isLoading: Boolean = true,
    problem: ChartProblem? = null,
    canEdit: Boolean = true,
) = NotesChartState(
    title = chart?.spec?.title ?: "ventas",
    chart = chart,
    isLoading = isLoading,
    isRefreshing = false,
    problem = problem,
    canEdit = canEdit,
    share = aDocumentShareState(),
    eventSink = {},
)
