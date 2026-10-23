/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.math.BigDecimal
import java.math.BigInteger

/** The chart types the server draws (`chartspec.TYPES`). */
enum class ChartType(val value: String) {
    BAR("bar"),
    LINE("line"),
    AREA("area"),
    PIE("pie"),
    DONUT("donut"),
    STACKED("stacked"),
    KPI("kpi"),
    TABLE("table");

    companion object {
        fun of(value: String): ChartType? = entries.firstOrNull { it.value == value.trim().lowercase() }
    }
}

/** A chart's own numbers: the labels' column, then one column per series. Cells as text, numbers as the file writes them ("1234.5"). */
data class ChartData(val columns: List<String>, val rows: List<List<String>>)

/** Where a chart reads its numbers each time it's drawn: the chat's records (or a CSV), filtered, over a period. */
data class ChartSource(
    val records: String = "",
    /** Equality filters, in the file's order. */
    val where: List<Pair<String, String>> = emptyList(),
    val period: String = "",
    val x: String = "",
    val y: String = "",
    val group: String = "",
)

/**
 * What the chart editor's form holds. Exactly one of [data] (frozen numbers) and [source] (read each time) is written,
 * as [usesSource] says; the other is kept while the editor is open, so switching tabs loses nothing.
 */
data class ChartForm(
    val title: String = "",
    val type: String = ChartType.BAR.value,
    val unit: String = "",
    val caption: String = "",
    val colors: List<String> = emptyList(),
    val compare: String = "",
    val series: List<String> = emptyList(),
    val data: ChartData = ChartData(emptyList(), emptyList()),
    val source: ChartSource = ChartSource(),
    val usesSource: Boolean = false,
) {
    /** The series drawn: [series] when the file names some, else every column after the labels. */
    val drawnSeries: List<String> get() = series.ifEmpty { data.columns.drop(1) }
}

/**
 * A `.chart` file (YAML, see the Notes API's Charts) read into a [ChartForm] and written back from one.
 *
 * Writing keeps the file as the person or Ari left it wherever the form didn't change it: the keys' order, unknown keys,
 * comments and blank lines. Only a key whose value changed is written again (in plain, server-safe YAML), and a new key
 * goes after the key that comes before it in the documented order.
 */
class ChartFile private constructor(
    private val blocks: List<Block>,
    val form: ChartForm,
) {
    /** One top-level key with its lines (and the comments and blank lines just above it); [key] `null` for the file's tail. */
    private data class Block(val key: String?, val text: String)

    /** The file's text for [newForm], changing only what differs from what was read. */
    fun write(newForm: ChartForm): String {
        val wanted = ChartYaml.entries(newForm)
        val before = ChartYaml.entries(form)
        val out = mutableListOf<Block>()
        for (block in blocks) {
            val key = block.key
            when {
                key == null || key !in KNOWN_KEYS -> out += block
                key !in wanted -> Unit
                // Unchanged: as it was, comments and all.
                wanted[key] == before[key] -> out += block
                else -> out += Block(key, leadingComments(block.text) + wanted.getValue(key))
            }
        }
        for ((key, text) in wanted) {
            if (out.any { it.key == key }) continue
            // After the key that comes before it in the documented order, else first.
            val order = KNOWN_KEYS.indexOf(key)
            val after = out.indexOfLast { it.key != null && it.key in KNOWN_KEYS && KNOWN_KEYS.indexOf(it.key) < order }
            val at = if (after >= 0) after + 1 else out.indexOfFirst { it.key != null }.coerceAtLeast(0)
            out.add(at, Block(key, text))
        }
        return out.joinToString("") { it.text.ensureNewline() }.trimEnd('\n') + "\n"
    }

    companion object {
        /** The keys the server accepts, in the order Ari writes them (`chartspec.dump`). */
        val KNOWN_KEYS = listOf("title", "type", "unit", "caption", "compare", "source", "data", "series", "colors")

        /** A new chart named [title]: bars, with one empty row under columns named in the app's language. */
        fun newText(title: String, labelColumn: String, valueColumn: String): String {
            val form = ChartForm(title = title, data = ChartData(listOf(labelColumn, valueColumn), listOf(listOf("", "0"))))
            return ChartFile(emptyList(), ChartForm()).write(form)
        }

        /** Reads a chart file; fails when it isn't YAML the form can show (then the editor offers the file's text only). */
        fun read(text: String): Result<ChartFile> = runCatchingExceptions {
            val root = Load(LoadSettings.builder().setAllowDuplicateKeys(false).build()).loadFromString(text)
            val map = root as? Map<*, *> ?: if (root == null) emptyMap<Any, Any>() else error("not a mapping")
            ChartFile(splitBlocks(text), formOf(map))
        }

        private fun formOf(map: Map<*, *>): ChartForm {
            val data = map["data"] as? Map<*, *>
            val source = map["source"] as? Map<*, *>
            return ChartForm(
                title = scalar(map["title"]),
                type = scalar(map["type"]).ifEmpty { ChartType.BAR.value },
                unit = scalar(map["unit"]),
                caption = scalar(map["caption"]),
                colors = list(map["colors"]),
                compare = scalar(map["compare"]),
                series = list(map["series"]),
                data = ChartData(
                    columns = list(data?.get("columns")),
                    rows = (data?.get("rows") as? List<*>).orEmpty().map { row -> (row as? List<*>).orEmpty().map(::scalar) },
                ),
                source = ChartSource(
                    records = scalar(source?.get("records")),
                    where = (source?.get("where") as? Map<*, *>).orEmpty().map { (k, v) -> scalar(k) to scalar(v) },
                    period = scalar(source?.get("period")),
                    x = scalar(source?.get("x")),
                    y = scalar(source?.get("y")),
                    group = scalar(source?.get("group")),
                ),
                usesSource = source != null,
            )
        }

        private fun list(value: Any?): List<String> = when (value) {
            is List<*> -> value.map(::scalar)
            null -> emptyList()
            else -> listOf(scalar(value))
        }

        /** A value as the form shows it: numbers in plain form ("12000", "15400.5"), nothing as empty. */
        fun scalar(value: Any?): String = when (value) {
            null -> ""
            is Int, is Long, is BigInteger -> value.toString()
            is Float, is Double -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            is BigDecimal -> value.stripTrailingZeros().toPlainString()
            else -> value.toString()
        }

        /** The file cut at each top-level key; comments and blank lines go with the key below them. */
        private fun splitBlocks(text: String): List<Block> {
            val blocks = mutableListOf<Block>()
            val pending = StringBuilder()
            var key: String? = null
            val current = StringBuilder()
            for (line in text.lines().let { if (text.endsWith("\n")) it.dropLast(1) else it }) {
                val topKey = TOP_KEY.find(line)?.groupValues?.get(1)
                when {
                    topKey != null -> {
                        if (key != null || current.isNotEmpty()) blocks += Block(key, current.toString())
                        current.clear()
                        current.append(pending).append(line).append('\n')
                        pending.clear()
                        key = topKey
                    }
                    line.isBlank() || line.trimStart().startsWith("#") && !line.startsWith(" ") -> pending.append(line).append('\n')
                    else -> {
                        current.append(pending).append(line).append('\n')
                        pending.clear()
                    }
                }
            }
            if (key != null || current.isNotEmpty()) blocks += Block(key, current.toString())
            if (pending.isNotEmpty()) blocks += Block(null, pending.toString())
            return blocks
        }

        private fun leadingComments(block: String): String =
            block.lineSequence().takeWhile { it.isBlank() || it.trimStart().startsWith("#") }.joinToString("") { "$it\n" }

        private fun String.ensureNewline() = if (endsWith("\n")) this else "$this\n"

        private val TOP_KEY = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*:(\s|$)""")
    }
}
