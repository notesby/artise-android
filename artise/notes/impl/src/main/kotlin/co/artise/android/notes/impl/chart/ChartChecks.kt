/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

import androidx.compose.runtime.Immutable

/**
 * The server's rules for a chart that can be drawn (`chartspec.py`), checked on the phone so nobody saves one that
 * can't: the first broken rule, in the server's order, worded in the app's language by the view.
 */
@Immutable
sealed interface ChartCheck {
    data object TooBig : ChartCheck

    data object Type : ChartCheck

    data object TitleTooLong : ChartCheck

    data object Title : ChartCheck

    /** `compare` on a chart that isn't a kpi. */
    data object Compare : ChartCheck

    data class Color(val value: String) : ChartCheck

    /** A sourced chart needs its records, period, x and y. */
    data object Source : ChartCheck

    data object Columns : ChartCheck

    data object Names : ChartCheck

    data object NoRows : ChartCheck

    data object TooManyRows : ChartCheck

    /** [row] from 1, as the server counts; it should have [values] cells. */
    data class RowLength(val row: Int, val values: Int) : ChartCheck

    data object TooManySeries : ChartCheck

    data class UnknownSeries(val name: String) : ChartCheck

    data object OneSeries : ChartCheck

    /** [row] from 1, as the server counts; [column] the series' name; [value] what's in the cell. */
    data class NotANumber(val row: Int, val column: String, val value: String) : ChartCheck

    data object Negative : ChartCheck
}

object ChartChecks {
    const val MAX_SERIES = 8
    const val MAX_ROWS = 500
    const val MAX_BYTES = 64 * 1024
    const val MAX_TITLE = 120

    private val ONE_SERIES_TYPES = setOf(ChartType.PIE.value, ChartType.DONUT.value)
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    /** The first rule [form] (written as [text]) breaks, or `null` when the server will draw it. */
    fun check(form: ChartForm, text: String): ChartCheck? {
        if (text.toByteArray().size >= MAX_BYTES) return ChartCheck.TooBig
        if (ChartType.of(form.type) == null) return ChartCheck.Type
        if (form.title.trim().length > MAX_TITLE) return ChartCheck.TitleTooLong
        if (form.title.isBlank()) return ChartCheck.Title
        if (form.compare.isNotBlank() && form.type != ChartType.KPI.value) return ChartCheck.Compare
        form.colors.firstOrNull { !HEX.matches(it) }?.let { return ChartCheck.Color(it) }
        if (form.usesSource) {
            val source = form.source
            return ChartCheck.Source.takeIf { listOf(source.records, source.period, source.x, source.y).any { it.isBlank() } }
        }
        return dataCheck(form)
    }

    private fun dataCheck(form: ChartForm): ChartCheck? {
        val columns = form.data.columns
        val rows = form.data.rows
        if (columns.size < 2) return ChartCheck.Columns
        if (columns.any { it.isBlank() } || columns.map { it.trim() }.toSet().size != columns.size) return ChartCheck.Names
        if (rows.isEmpty()) return ChartCheck.NoRows
        if (rows.size > MAX_ROWS) return ChartCheck.TooManyRows
        rows.indexOfFirst { it.size != columns.size }.takeIf { it >= 0 }?.let { return ChartCheck.RowLength(it + 1, columns.size) }
        val drawn = form.drawnSeries
        if (drawn.size > MAX_SERIES) return ChartCheck.TooManySeries
        drawn.firstOrNull { it !in columns.drop(1) }?.let { return ChartCheck.UnknownSeries(it) }
        val isOneSeries = form.type in ONE_SERIES_TYPES
        if (isOneSeries && drawn.size > 1) return ChartCheck.OneSeries
        val indexes = drawn.map { columns.indexOf(it) }
        rows.forEachIndexed { n, row ->
            for (index in indexes) {
                val cell = row[index]
                if (!ChartYaml.isNumber(cell)) return ChartCheck.NotANumber(n + 1, columns[index], cell)
            }
        }
        if (isOneSeries && rows.any { row -> indexes.any { row[it].startsWith("-") } }) return ChartCheck.Negative
        return null
    }
}
