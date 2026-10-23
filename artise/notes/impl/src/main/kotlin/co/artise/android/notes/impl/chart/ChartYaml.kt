/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

/**
 * Writes a [ChartForm]'s keys as YAML the server reads the same way (it uses YAML 1.1): text that could read as
 * something else (a number, a date, yes/no, a colour's `#`) is quoted; numbers in the series' cells stay numbers.
 */
internal object ChartYaml {
    /** Each key the form has, with its lines, in the documented order. Empty optional keys are left out. */
    fun entries(form: ChartForm): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        out["title"] = "title: ${text(form.title)}\n"
        out["type"] = "type: ${text(form.type)}\n"
        if (form.unit.isNotBlank()) out["unit"] = "unit: ${text(form.unit)}\n"
        if (form.caption.isNotBlank()) out["caption"] = "caption: ${text(form.caption)}\n"
        if (form.compare.isNotBlank()) out["compare"] = "compare: ${text(form.compare)}\n"
        if (form.usesSource) out["source"] = source(form.source) else out["data"] = data(form)
        if (form.series.isNotEmpty()) out["series"] = "series: ${flow(form.series.map(::text))}\n"
        if (form.colors.isNotEmpty()) out["colors"] = "colors: ${flow(form.colors.map(::text))}\n"
        return out
    }

    private fun data(form: ChartForm): String {
        val columns = form.data.columns
        val numeric = form.drawnSeries.toSet()
        val header = "data:\n  columns: ${flow(columns.map(::text))}\n"
        if (form.data.rows.isEmpty()) return header + "  rows: []\n"
        val rows = form.data.rows.joinToString("") { row ->
            val cells = row.mapIndexed { index, cell ->
                // The labels' column is text; a series' cell is a number when it reads as one.
                if (index > 0 && columns.getOrNull(index) in numeric && isNumber(cell)) cell else text(cell)
            }
            "    - ${flow(cells)}\n"
        }
        return header + "  rows:\n" + rows
    }

    private fun source(source: ChartSource): String = buildString {
        append("source:\n")
        append("  records: ${text(source.records)}\n")
        if (source.where.isNotEmpty()) {
            append("  where: {")
            append(source.where.joinToString(", ") { (field, value) -> "${text(field)}: ${text(value)}" })
            append("}\n")
        }
        if (source.period.isNotBlank()) append("  period: ${text(source.period)}\n")
        if (source.x.isNotBlank()) append("  x: ${text(source.x)}\n")
        if (source.y.isNotBlank()) append("  y: ${text(source.y)}\n")
        if (source.group.isNotBlank()) append("  group: ${text(source.group)}\n")
    }

    private fun flow(items: List<String>) = items.joinToString(", ", prefix = "[", postfix = "]")

    /** A plain number, as the file stores them: `1234.5`, `-3`, `0`. */
    fun isNumber(value: String) = PLAIN_NUMBER.matches(value)

    /** [value] as a YAML string: plain when it can only read as that text, else double-quoted. */
    fun text(value: String): String = if (isPlainSafe(value)) value else quoted(value)

    private fun isPlainSafe(value: String): Boolean = value.isNotEmpty() &&
        value == value.trim() &&
        value.lowercase() !in SPECIAL_WORDS &&
        // A number, a date ("2026-05", "2026-05-01"), a time: anything that starts like one is quoted.
        !value.first().isDigit() &&
        !LOOKS_TYPED.matches(value) &&
        value.first() !in START_INDICATORS &&
        value.none { it in INSIDE_INDICATORS || it.isISOControl() } &&
        ": " !in value && " #" !in value && !value.endsWith(":")

    private fun quoted(value: String): String = buildString {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\t' -> append("\\t")
                char.isISOControl() -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private val PLAIN_NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?""")

    // Would read as a number, a date or a time in YAML 1.1 (the server's reader), not as text.
    private val LOOKS_TYPED =
        Regex("""[-+]?(\.\d+|\d[\d_,:.]*([eE][-+]?\d+)?|\.inf|\.nan)|\d{4}-\d\d?-\d\d?([Tt ].*)?|0x[0-9a-fA-F_]+|0o?[0-7_]+""", RegexOption.IGNORE_CASE)

    // YAML 1.1's booleans and nulls.
    private val SPECIAL_WORDS = setOf("y", "yes", "n", "no", "true", "false", "on", "off", "null", "~", "")

    private val START_INDICATORS = setOf('-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`', ' ')
    private val INSIDE_INDICATORS = setOf(',', '[', ']', '{', '}', '#', '"', '\'')
}
