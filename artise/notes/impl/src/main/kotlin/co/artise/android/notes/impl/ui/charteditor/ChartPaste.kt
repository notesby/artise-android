/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

/** Cells copied from a spreadsheet: a line per row, tabs between cells (Sheets, Excel and Numbers all copy so). */
internal object ChartPaste {
    /** A pasted table: [columns] when its first line names them (no number after its first cell), then the rows. */
    data class Table(val columns: List<String>?, val rows: List<List<String>>)

    fun table(text: String): Table? {
        val lines = cells(text).takeIf { it.isNotEmpty() } ?: return null
        val first = lines.first()
        val isHeader = first.size > 1 && first.drop(1).none { cell -> NUMBER.matches(cell.replace(",", "").replace(" ", "")) }
        return if (isHeader) Table(first, lines.drop(1)) else Table(null, lines)
    }

    private val NUMBER = Regex("""[-+]?\$?\d+([.]\d+)?""")

    fun cells(text: String): List<List<String>> = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd('\n')
        .split('\n')
        .filter { it.isNotBlank() }
        .map { line -> line.split('\t').map { it.trim() } }
}
