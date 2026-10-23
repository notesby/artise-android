/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.dashboard

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** One piece of a dashboard's body as the editor lays it out; [line] is where it starts in the note (from 0). */
@Immutable
sealed interface DashboardBlock {
    val line: Int

    /** Markdown between the charts, as written. */
    data class Text(override val line: Int, val text: String) : DashboardBlock

    /** A line that is only `![[<ref>]]`; [path] is the chart it means (a bare name is `charts/<name>`). */
    data class Chart(override val line: Int, val ref: String, val path: String) : DashboardBlock

    /** The charts between `::: columns` and `:::`, side by side on a wide screen. */
    data class Columns(override val line: Int, val charts: ImmutableList<Chart>) : DashboardBlock
}

/** What's wrong in a dashboard, as the server tells Ari (`dashboards.py`); [line] from 0, `null` for the whole note. */
@Immutable
sealed interface DashboardProblem {
    val line: Int?

    data class MissingChart(override val line: Int, val ref: String) : DashboardProblem

    data class UnclosedColumns(override val line: Int) : DashboardProblem

    data class NestedColumns(override val line: Int) : DashboardProblem

    /** Over [DashboardText.MAX_CHARTS]: the rest aren't drawn. */
    data class TooManyCharts(val count: Int) : DashboardProblem {
        override val line: Int? get() = null
    }

    data class BadAccent(val value: String) : DashboardProblem {
        override val line: Int? get() = null
    }

    data class BadPalette(val value: String) : DashboardProblem {
        override val line: Int? get() = null
    }

    data class BadMode(val value: String) : DashboardProblem {
        override val line: Int? get() = null
    }
}

/** A dashboard's own look (front matter `theme`); empty values leave the workspace's. */
@Immutable
data class DashboardTheme(val accent: String = "", val palette: ImmutableList<String> = persistentListOf(), val mode: String = "") {
    val isEmpty: Boolean get() = accent.isEmpty() && palette.isEmpty() && mode.isEmpty()
}

/**
 * Reads and changes a dashboard note's text: its charts (`![[charts/x.chart]]` lines), its `::: columns` blocks and its
 * front matter `theme`, the way the server draws them. Every change is a change to the text, so the Markdown stays the
 * one source and the raw text the person can always edit.
 */
object DashboardText {
    const val MAX_CHARTS = 24
    const val MAX_PALETTE = 8
    val MODES = listOf("light", "dark", "auto")

    private val EMBED = Regex("""^\s*!\[\[([^\]|#]+?)(?:\|[^\]]*)?\]\]\s*$""")
    private val OPEN = Regex("""^\s*:::\s*columns\s*$""", RegexOption.IGNORE_CASE)
    private val CLOSE = Regex("""^\s*:::\s*$""")
    private val HEX = Regex("""^#[0-9A-Fa-f]{6}$""")

    /** Whether the note's front matter says `type: dashboard` (or `type: report`). */
    fun isDashboard(text: String): Boolean = frontMatterValue(text, "type")?.lowercase() in setOf("dashboard", "report")

    /** The chart an embed means: the path as written, else `charts/<name>` for a bare name. */
    fun chartPath(ref: String, existing: Collection<String>): String {
        val clean = ref.trim().trimStart('/')
        return if ('/' !in clean && clean !in existing) "charts/$clean" else clean
    }

    /** The body's blocks, in order. [existing] are the chat's files, to tell a bare name's folder. */
    fun blocks(text: String, existing: Collection<String>): ImmutableList<DashboardBlock> {
        val lines = text.lines()
        val start = bodyStart(lines)
        val out = mutableListOf<DashboardBlock>()
        var group: MutableList<DashboardBlock.Chart>? = null
        var groupLine = 0
        val para = mutableListOf<String>()
        var paraLine = start
        fun flush() {
            if (para.any { it.isNotBlank() } && group == null) out += DashboardBlock.Text(paraLine, para.joinToString("\n"))
            para.clear()
        }
        for (index in start until lines.size) {
            val line = lines[index]
            val ref = EMBED.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.endsWith(".chart", ignoreCase = true) }
            when {
                OPEN.matches(line) -> {
                    flush()
                    if (group == null) {
                        group = mutableListOf()
                        groupLine = index
                    }
                }
                CLOSE.matches(line) && group != null -> {
                    if (group.isNotEmpty()) out += DashboardBlock.Columns(groupLine, group.toImmutableList())
                    group = null
                }
                ref != null -> {
                    flush()
                    val chart = DashboardBlock.Chart(index, ref, chartPath(ref, existing))
                    group?.add(chart) ?: out.add(chart)
                }
                else -> {
                    if (para.isEmpty()) paraLine = index
                    para += line
                }
            }
            if (para.isEmpty()) paraLine = index + 1
        }
        flush()
        group?.takeIf { it.isNotEmpty() }?.let { out += DashboardBlock.Columns(groupLine, it.toImmutableList()) }
        return out.toImmutableList()
    }

    /** The same problems the server finds, with the line each is on. */
    fun problems(text: String, existing: Collection<String>): ImmutableList<DashboardProblem> {
        val lines = text.lines()
        val out = mutableListOf<DashboardProblem>()
        var openAt: Int? = null
        var charts = 0
        for (index in bodyStart(lines) until lines.size) {
            val line = lines[index]
            val ref = EMBED.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.endsWith(".chart", ignoreCase = true) }
            when {
                OPEN.matches(line) -> if (openAt != null) out += DashboardProblem.NestedColumns(index) else openAt = index
                CLOSE.matches(line) && openAt != null -> openAt = null
                ref != null -> {
                    charts++
                    if (chartPath(ref, existing) !in existing) out += DashboardProblem.MissingChart(index, ref)
                }
            }
        }
        openAt?.let { out += DashboardProblem.UnclosedColumns(it) }
        if (charts > MAX_CHARTS) out += DashboardProblem.TooManyCharts(charts)
        val theme = theme(text)
        if (theme.accent.isNotEmpty() && !HEX.matches(theme.accent)) out += DashboardProblem.BadAccent(theme.accent)
        theme.palette.firstOrNull { !HEX.matches(it) }?.let { out += DashboardProblem.BadPalette(it) }
        if (theme.mode.isNotEmpty() && theme.mode !in MODES) out += DashboardProblem.BadMode(theme.mode)
        return out.toImmutableList()
    }

    /** Takes line [line] out (a chart the person long-pressed). */
    fun removeLine(text: String, line: Int): String {
        val lines = text.lines().toMutableList()
        if (line !in lines.indices) return text
        lines.removeAt(line)
        return lines.joinToString("\n")
    }

    /** Adds a chart's line at the end of the note, on a line of its own. */
    fun insertChart(text: String, path: String): String {
        val body = text.trimEnd('\n')
        val separator = if (body.isEmpty() || isFrontMatterOnly(body)) "\n" else "\n\n"
        return "$body$separator![[$path]]\n"
    }

    /** Moves the chart on [line] one place up (`-1`) or down (`+1`) within its columns block; nothing outside it. */
    fun moveInColumns(text: String, line: Int, delta: Int): String {
        val blocks = blocks(text, emptyList())
        val group = blocks.filterIsInstance<DashboardBlock.Columns>().firstOrNull { columns -> columns.charts.any { it.line == line } } ?: return text
        val index = group.charts.indexOfFirst { it.line == line }
        val other = group.charts.getOrNull(index + delta) ?: return text
        val lines = text.lines().toMutableList()
        val moved = lines[line]
        lines[line] = lines[other.line]
        lines[other.line] = moved
        return lines.joinToString("\n")
    }

    /** The front matter's `theme`, as written: `theme: {accent: "#22408A", palette: [...], mode: auto}` or indented lines. */
    fun theme(text: String): DashboardTheme {
        val lines = text.lines()
        val (start, end) = themeLines(lines) ?: return DashboardTheme()
        val first = lines[start].substringAfter(':').trim()
        // Indented lines under `theme:` read as the flow form's fields.
        val inner = if (first.isNotEmpty()) first.removePrefix("{").removeSuffix("}") else lines.subList(start + 1, end).joinToString(", ") { it.trim() }
        fun field(name: String): String {
            val found = Regex("""(?:^|,)\s*$name\s*:\s*("[^"]*"|'[^']*'|[^,\[\]]*)""").find(inner) ?: return ""
            return found.groupValues[1].trim().trim('"', '\'')
        }
        val listed = Regex("""palette\s*:\s*\[([^\]]*)]""").find(inner)?.groupValues?.get(1).orEmpty()
        val palette = listed.split(',').map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
        return DashboardTheme(accent = field("accent"), palette = palette.toImmutableList(), mode = field("mode"))
    }

    /** The note with its front matter's `theme` set to [theme] (or taken out when it's empty); the rest as it was. */
    fun withTheme(text: String, theme: DashboardTheme): String {
        val parts = buildList {
            if (theme.accent.isNotEmpty()) add("accent: \"${theme.accent}\"")
            if (theme.palette.isNotEmpty()) add("palette: [" + theme.palette.take(MAX_PALETTE).joinToString(", ") { "\"$it\"" } + "]")
            if (theme.mode.isNotEmpty()) add("mode: ${theme.mode}")
        }
        val line = if (parts.isEmpty()) null else "theme: {" + parts.joinToString(", ") + "}"
        val lines = text.lines().toMutableList()
        val end = frontMatterEnd(lines)
            // No front matter: one, so the theme has a place.
            ?: return (listOf("---") + listOfNotNull(line) + listOf("---") + lines).joinToString("\n")
        val existing = themeLines(lines)
        when {
            existing != null -> {
                repeat(existing.second - existing.first) { lines.removeAt(existing.first) }
                if (line != null) lines.add(existing.first, line)
            }
            line != null -> lines.add(end, line)
        }
        return lines.joinToString("\n")
    }

    /** Where `theme:` is in the front matter: its line, and the line after its last indented one; `null` when it has none. */
    private fun themeLines(lines: List<String>): Pair<Int, Int>? {
        val end = frontMatterEnd(lines) ?: return null
        val start = (1 until end).firstOrNull { lines[it].startsWith("theme:") } ?: return null
        var after = start + 1
        while (after < end && lines[after].isNotEmpty() && lines[after].first().isWhitespace()) after++
        return start to after
    }

    /** `---\ntype: dashboard\ntitle: <name>\n---\n`: a new dashboard. */
    fun newDashboard(title: String): String = "---\ntype: dashboard\ntitle: ${frontMatterText(title)}\n---\n"

    private fun frontMatterText(value: String): String =
        if (value.any { it in ":#[]{},\"'" } || value != value.trim()) "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else value

    private fun frontMatterValue(text: String, key: String): String? {
        val lines = text.lines()
        val end = frontMatterEnd(lines) ?: return null
        return (1 until end).map { lines[it] }.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.let {
            if (key == "type") it.trim('"', '\'') else it
        }
    }

    /** The index of the front matter's closing `---`, or `null` when the note has none. */
    private fun frontMatterEnd(lines: List<String>): Int? {
        if (lines.firstOrNull()?.trimEnd() != "---") return null
        return (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }
    }

    private fun bodyStart(lines: List<String>): Int = frontMatterEnd(lines)?.plus(1) ?: 0

    private fun isFrontMatterOnly(text: String): Boolean {
        val lines = text.lines()
        return frontMatterEnd(lines) == lines.lastIndex
    }
}
