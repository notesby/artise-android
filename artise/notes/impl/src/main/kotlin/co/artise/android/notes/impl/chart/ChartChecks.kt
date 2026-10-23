/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

/**
 * The server's rules for a chart that can be drawn (`chartspec.py`), checked on the phone so nobody saves one that
 * can't: the first broken rule, worded in the app's language by the view.
 */
sealed interface ChartCheck {
    data object Title : ChartCheck

    data object Columns : ChartCheck

    data object Names : ChartCheck

    /** [row] from 1, as the server counts; [column] the series' name; [value] what's in the cell. */
    data class NotANumber(val row: Int, val column: String, val value: String) : ChartCheck

    data object TooManySeries : ChartCheck

    data object TooManyRows : ChartCheck

    data object OneSeries : ChartCheck

    data object Negative : ChartCheck

    /** A sourced chart needs its records, period, x and y. */
    data object Source : ChartCheck

    data object TooBig : ChartCheck
}

object ChartChecks {
    const val MAX_SERIES = 8
    const val MAX_ROWS = 500
    const val MAX_BYTES = 64 * 1024

    private val ONE_SERIES_TYPES = setOf(ChartType.PIE.value, ChartType.DONUT.value)

    /** The first rule [form] (written as [text]) breaks, or `null` when the server will draw it. */
    fun check(form: ChartForm, text: String): ChartCheck? {
        if (form.title.isBlank()) return ChartCheck.Title
        if (text.toByteArray().size >= MAX_BYTES) return ChartCheck.TooBig
        if (form.usesSource) {
            val source = form.source
            return ChartCheck.Source.takeIf { listOf(source.records, source.period, source.x, source.y).any { it.isBlank() } }
        }
        val columns = form.data.columns
        if (columns.size < 2) return ChartCheck.Columns
        if (columns.any { it.isBlank() } || columns.map { it.trim() }.toSet().size != columns.size) return ChartCheck.Names
        if (form.data.rows.size > MAX_ROWS) return ChartCheck.TooManyRows
        val drawn = form.drawnSeries
        if (drawn.size > MAX_SERIES) return ChartCheck.TooManySeries
        val isOneSeries = form.type in ONE_SERIES_TYPES
        if (isOneSeries && drawn.size > 1) return ChartCheck.OneSeries
        val indexes = drawn.map { columns.indexOf(it) }.filter { it > 0 }
        form.data.rows.forEachIndexed { n, row ->
            for (index in indexes) {
                val cell = row.getOrNull(index).orEmpty()
                if (!ChartYaml.isNumber(cell)) return ChartCheck.NotANumber(n + 1, columns[index], cell)
            }
        }
        if (isOneSeries && form.data.rows.any { row -> indexes.any { row.getOrNull(it).orEmpty().startsWith("-") } }) return ChartCheck.Negative
        return null
    }
}
