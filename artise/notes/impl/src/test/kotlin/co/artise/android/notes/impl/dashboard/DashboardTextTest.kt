/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.dashboard

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class DashboardTextTest {
    private val paths = listOf("charts/ventas.chart", "charts/cobros.chart", "charts/clientes.chart", "Octubre.md")
    private val octubre = """
        ---
        type: dashboard
        title: Octubre 2026
        theme: {accent: "#22408A", palette: ["#22408A", "#EB6834"], mode: auto}
        ---
        ## Ventas
        ![[charts/ventas.chart]]
        ::: columns
        ![[charts/cobros.chart]]
        ![[clientes.chart|Clientes]]
        :::
        Notas del mes.
    """.trimIndent()

    @Test
    fun `a dashboard is told by its front matter`() {
        assertThat(DashboardText.isDashboard(octubre)).isTrue()
        assertThat(DashboardText.isDashboard("---\ntype: report\n---\n")).isTrue()
        assertThat(DashboardText.isDashboard("---\ntitle: x\n---\ntype: dashboard")).isFalse()
        assertThat(DashboardText.isDashboard("# Mole")).isFalse()
    }

    /** The body as the server draws it: text, charts (a bare name is in charts/), and columns side by side. */
    @Test
    fun `the body is laid out in blocks`() {
        val blocks = DashboardText.blocks(octubre, paths)
        assertThat(blocks).hasSize(4)
        assertThat((blocks[0] as DashboardBlock.Text).text).isEqualTo("## Ventas")
        assertThat(blocks[0].line).isEqualTo(5)
        assertThat(blocks[1]).isEqualTo(DashboardBlock.Chart(6, "charts/ventas.chart", "charts/ventas.chart"))
        val columns = blocks[2] as DashboardBlock.Columns
        assertThat(columns.charts.map { it.path }).containsExactly("charts/cobros.chart", "charts/clientes.chart").inOrder()
        assertThat(columns.charts.map { it.line }).containsExactly(8, 9).inOrder()
        assertThat((blocks[3] as DashboardBlock.Text).text).isEqualTo("Notas del mes.")
    }

    /** Each problem the server tells Ari about is found, on its line. */
    @Test
    fun `problems are marked where they are`() {
        assertThat(DashboardText.problems(octubre, paths)).isEmpty()
        val broken = """
            ---
            type: dashboard
            theme: {accent: blue, palette: ["#22408A", "#12"], mode: sepia}
            ---
            ![[charts/nada.chart]]
            ::: columns
            ::: columns
            ![[charts/ventas.chart]]
        """.trimIndent()
        assertThat(DashboardText.problems(broken, paths)).containsExactly(
            DashboardProblem.MissingChart(4, "charts/nada.chart"),
            DashboardProblem.NestedColumns(6),
            DashboardProblem.UnclosedColumns(5),
            DashboardProblem.BadAccent("blue"),
            DashboardProblem.BadPalette("#12"),
            DashboardProblem.BadMode("sepia"),
        ).inOrder()
        val many = "---\ntype: dashboard\n---\n" + (1..25).joinToString("\n") { "![[charts/ventas.chart]]" }
        assertThat(DashboardText.problems(many, paths)).containsExactly(DashboardProblem.TooManyCharts(25))
    }

    /** + Chart: the exact `![[path]]` line, on a line of its own at the end. */
    @Test
    fun `adding a chart writes its line`() {
        assertThat(DashboardText.insertChart(octubre, "charts/cobros.chart")).isEqualTo("$octubre\n\n![[charts/cobros.chart]]\n")
        assertThat(DashboardText.insertChart("---\ntype: dashboard\n---\n", "charts/a.chart")).isEqualTo("---\ntype: dashboard\n---\n![[charts/a.chart]]\n")
    }

    @Test
    fun `a chart's line is taken out, and moved within its columns`() {
        assertThat(DashboardText.removeLine(octubre, 6)).doesNotContain("![[charts/ventas.chart]]")
        val moved = DashboardText.moveInColumns(octubre, 8, +1)
        assertThat(moved).contains("::: columns\n![[clientes.chart|Clientes]]\n![[charts/cobros.chart]]\n:::")
        // Not past the block's ends, and nothing outside a columns block
        assertThat(DashboardText.moveInColumns(octubre, 8, -1)).isEqualTo(octubre)
        assertThat(DashboardText.moveInColumns(octubre, 6, +1)).isEqualTo(octubre)
    }

    /** The theme reads from the front matter, written inline or as indented lines. */
    @Test
    fun `the theme is read`() {
        assertThat(DashboardText.theme(octubre)).isEqualTo(DashboardTheme("#22408A", persistentListOf("#22408A", "#EB6834"), "auto"))
        val block = "---\ntype: dashboard\ntheme:\n  accent: \"#EB6834\"\n  mode: dark\n---\nHola"
        assertThat(DashboardText.theme(block)).isEqualTo(DashboardTheme("#EB6834", persistentListOf(), "dark"))
        assertThat(DashboardText.theme("# sin front matter")).isEqualTo(DashboardTheme())
    }

    /** The Theme sheet writes valid front matter: replaced in place, added when missing, taken out when empty. */
    @Test
    fun `the theme is written into the front matter`() {
        val dark = DashboardTheme("#000000", persistentListOf(), "dark")
        val replaced = DashboardText.withTheme(octubre, dark)
        assertThat(replaced).contains("title: Octubre 2026\ntheme: {accent: \"#000000\", mode: dark}\n---")
        assertThat(DashboardText.theme(replaced)).isEqualTo(dark)
        val added = DashboardText.withTheme("---\ntype: dashboard\n---\nHola", DashboardTheme(mode = "light"))
        assertThat(added).isEqualTo("---\ntype: dashboard\ntheme: {mode: light}\n---\nHola")
        assertThat(DashboardText.withTheme(added, DashboardTheme())).isEqualTo("---\ntype: dashboard\n---\nHola")
        // An indented theme is replaced whole, children too
        val block = "---\ntype: dashboard\ntheme:\n  accent: \"#EB6834\"\n  mode: dark\ntitle: x\n---\nHola"
        assertThat(DashboardText.withTheme(block, dark)).isEqualTo("---\ntype: dashboard\ntheme: {accent: \"#000000\", mode: dark}\ntitle: x\n---\nHola")
    }

    @Test
    fun `a new dashboard has its front matter`() {
        assertThat(DashboardText.newDashboard("Octubre")).isEqualTo("---\ntype: dashboard\ntitle: Octubre\n---\n")
        assertThat(DashboardText.newDashboard("Ventas: octubre")).isEqualTo("---\ntype: dashboard\ntitle: \"Ventas: octubre\"\n---\n")
        assertThat(DashboardText.isDashboard(DashboardText.newDashboard("x"))).isTrue()
    }
}
