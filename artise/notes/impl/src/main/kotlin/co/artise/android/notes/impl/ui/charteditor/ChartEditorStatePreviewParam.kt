/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.RecordType
import co.artise.android.notes.impl.chart.ChartCheck
import co.artise.android.notes.impl.chart.ChartData
import co.artise.android.notes.impl.chart.ChartForm
import co.artise.android.notes.impl.chart.ChartSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class ChartEditorStatePreviewParam : PreviewParameterProvider<ChartEditorState> {
    override val values: Sequence<ChartEditorState>
        get() = sequenceOf(
            aChartEditorState(),
            aChartEditorState(check = ChartCheck.NotANumber(2, "Ventas", "mucho")),
            aChartEditorState(
                form = aChartForm().copy(
                    usesSource = true,
                    source = ChartSource("payment", listOf("method" to "card"), "last 6 months", "month", "sum(amount)", "")
                ),
                recordTypes = persistentListOf(RecordType("payment", listOf("date", "amount", "method"), "date", "amount")),
            ),
            aChartEditorState(showFile = true, serverMessage = "row 2, Ventas: 'mucho' isn't a number"),
            aChartEditorState(isLoading = true),
        )
}

internal fun aChartForm() = ChartForm(
    title = "Ventas por mes",
    type = "bar",
    unit = "MXN",
    colors = listOf("#22408A"),
    data = ChartData(listOf("Mes", "Ventas"), listOf(listOf("2026-05", "12000"), listOf("2026-06", "15400.5"))),
)

internal fun aChartEditorState(
    form: ChartForm = aChartForm(),
    isLoading: Boolean = false,
    check: ChartCheck? = null,
    showFile: Boolean = false,
    serverMessage: String? = null,
    recordTypes: ImmutableList<RecordType> = persistentListOf(),
) = ChartEditorState(
    isNew = false,
    isLoading = isLoading,
    form = form,
    grid = form.data.rows.map { it.toImmutableList() }.toImmutableList(),
    showFile = showFile,
    fileText = "title: Ventas por mes\ntype: bar\n",
    fileUnreadable = false,
    check = check,
    serverMessage = serverMessage,
    preview = null,
    isPreviewing = false,
    recordTypes = recordTypes,
    isSaving = false,
    isDone = false,
    eventSink = {},
)
