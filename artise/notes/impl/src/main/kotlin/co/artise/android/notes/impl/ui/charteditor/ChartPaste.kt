/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

/** Cells copied from a spreadsheet: a line per row, tabs between cells (Sheets, Excel and Numbers all copy so). */
internal object ChartPaste {
    fun cells(text: String): List<List<String>> = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd('\n')
        .split('\n')
        .filter { it.isNotEmpty() }
        .map { line -> line.split('\t').map { it.trim() } }
}
