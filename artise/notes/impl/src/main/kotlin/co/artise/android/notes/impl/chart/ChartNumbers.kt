/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

import io.element.android.libraries.core.extensions.runCatchingExceptions
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Numbers as people type them in their own format (`1,234.50` in es-MX, `1.234,50` in pt-BR) and as the chart file
 * stores them: a dot, no thousands separator (`1234.5`).
 */
object ChartNumbers {
    /** [typed] as the file writes it, or `null` when it isn't a number in [locale] (nor in the file's own format). */
    fun toFile(typed: String, locale: Locale): String? {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val decimal = symbols.decimalSeparator
        // A non-breaking space is the thousands separator in some languages; people type a plain one.
        val grouping = symbols.groupingSeparator.let { if (it == ' ' || it == ' ') ' ' else it }
        val text = typed.trim().removePrefix("$").replace(' ', ' ').replace(' ', ' ').trim()
        if (text.isEmpty()) return null
        val g = Regex.escape(grouping.toString())
        val d = Regex.escape(decimal.toString())
        val grouped = Regex("""[-+]?\d{1,3}($g\d{3})+($d\d+)?""")
        val ungrouped = Regex("""[-+]?\d+($d\d*)?|[-+]?$d\d+""")
        val canonical = when {
            grouped.matches(text) -> text.replace(grouping.toString(), "").replace(decimal, '.')
            ungrouped.matches(text) -> text.replace(decimal, '.')
            // Pasted from the file itself, or a spreadsheet in another format: a plain dot.
            FILE_NUMBER.matches(text) -> text
            else -> return null
        }
        return runCatchingExceptions { BigDecimal(canonical.removeSuffix(".")).stripTrailingZeros().toPlainString() }.getOrNull()
            ?.let { if (it == "-0") "0" else it }
    }

    /** A file number shown for editing in [locale]: `1234.5` → `1234,5` in pt-BR; anything else as it is. */
    fun forEditing(fileValue: String, locale: Locale): String {
        if (!ChartYaml.isNumber(fileValue)) return fileValue
        val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return fileValue.replace('.', decimal)
    }

    private val FILE_NUMBER = Regex("""[-+]?\d+(\.\d+)?""")
}
