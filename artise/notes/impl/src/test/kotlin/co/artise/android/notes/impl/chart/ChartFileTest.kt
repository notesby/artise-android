/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class ChartFileTest {
    private val ventas = """
        # Ari: ventas de la tienda
        title: Ventas por mes
        type: bar            # bar | line
        unit: MXN
        data:
          columns: [Mes, Ventas, Gastos]
          rows:
            - [2026-05, 12000, 8000]
            - [2026-06, 15400.5, 9100]
        series: [Ventas]
        colors: ["#22408A"]

        # the end
    """.trimIndent() + "\n"

    /** Every field reads into the form: numbers as plain text, the series, the colours. */
    @Test
    fun `a chart file reads into the form`() {
        val form = ChartFile.read(ventas).getOrThrow().form
        assertThat(form.title).isEqualTo("Ventas por mes")
        assertThat(form.type).isEqualTo("bar")
        assertThat(form.unit).isEqualTo("MXN")
        assertThat(form.data.columns).containsExactly("Mes", "Ventas", "Gastos").inOrder()
        assertThat(form.data.rows).containsExactly(listOf("2026-05", "12000", "8000"), listOf("2026-06", "15400.5", "9100")).inOrder()
        assertThat(form.series).containsExactly("Ventas")
        assertThat(form.colors).containsExactly("#22408A")
        assertThat(form.usesSource).isFalse()
        assertThat(form.drawnSeries).containsExactly("Ventas")
    }

    /** Nothing changed: the very same text, byte for byte, comments and all. */
    @Test
    fun `an unchanged form writes the file as it was`() {
        val file = ChartFile.read(ventas).getOrThrow()
        assertThat(file.write(file.form)).isEqualTo(ventas)
    }

    /** One field changed: only its line is written again; a new one goes at the end, in the documented order. */
    @Test
    fun `a change rewrites only its key`() {
        val file = ChartFile.read(ventas).getOrThrow()
        val written = file.write(file.form.copy(title = "Ventas: mayo y junio", caption = "Sin IVA"))
        assertThat(written).isEqualTo(
            ventas
                .replace("title: Ventas por mes", "title: \"Ventas: mayo y junio\"")
                .replace("colors: [\"#22408A\"]\n", "colors: [\"#22408A\"]\ncaption: Sin IVA\n"),
        )
        assertThat(ChartFile.read(written).getOrThrow().form.title).isEqualTo("Ventas: mayo y junio")
        // Emptied: gone
        assertThat(file.write(file.form.copy(unit = ""))).isEqualTo(ventas.replace("unit: MXN\n", ""))
    }

    /** The data block is written in plain YAML the server reads the same: labels as text, series' numbers as numbers. */
    @Test
    fun `changed data is written back`() {
        val file = ChartFile.read(ventas).getOrThrow()
        val data = ChartData(listOf("Mes", "Ventas", "Gastos"), listOf(listOf("2026-07", "1234.5", "no"), listOf("2026-08-01", "-3", "7")))
        val written = file.write(file.form.copy(data = data, series = emptyList()))
        assertThat(written).contains(
            """
            data:
              columns: [Mes, Ventas, Gastos]
              rows:
                - ["2026-07", 1234.5, "no"]
                - ["2026-08-01", -3, 7]
            """.trimIndent(),
        )
        assertThat(written).doesNotContain("series:")
        val back = ChartFile.read(written).getOrThrow().form
        assertThat(back.data).isEqualTo(data)
    }

    /** A sourced chart: `source` replaces `data`, in the documented place, and reads back. */
    @Test
    fun `switching to records writes a source instead of data`() {
        val file = ChartFile.read(ventas).getOrThrow()
        val source =
            ChartSource(records = "payment", where = listOf("method" to "card"), period = "last 6 months", x = "month", y = "sum(amount)", group = "method")
        val written = file.write(file.form.copy(usesSource = true, source = source))
        assertThat(written).doesNotContain("data:")
        assertThat(written).contains(
            """
            source:
              records: payment
              where: {method: card}
              period: last 6 months
              x: month
              y: sum(amount)
              group: method
            """.trimIndent(),
        )
        // A new key: at the end
        assertThat(written.indexOf("source:")).isGreaterThan(written.indexOf("colors:"))
        assertThat(ChartFile.read(written).getOrThrow().form.source).isEqualTo(source)
    }

    @Test
    fun `a new chart starts with one row and no file until saved`() {
        val text = ChartFile.newText("Ventas", "Etiqueta", "Valor")
        assertThat(text).isEqualTo("title: Ventas\ntype: bar\ndata:\n  columns: [Etiqueta, Valor]\n  rows:\n    - [\"\", 0]\n")
        assertThat(ChartFile.read(text).getOrThrow().form.data.rows).containsExactly(listOf("", "0"))
    }

    /** Text that would read as something else is quoted; plain words stay plain. */
    @Test
    fun `text is quoted only when it has to be`() {
        assertThat(ChartYaml.text("Ventas por mes")).isEqualTo("Ventas por mes")
        assertThat(ChartYaml.text("Año 2026")).isEqualTo("Año 2026")
        assertThat(ChartYaml.text("2026")).isEqualTo("\"2026\"")
        assertThat(ChartYaml.text("2026-05")).isEqualTo("\"2026-05\"")
        assertThat(ChartYaml.text("1er trimestre")).isEqualTo("\"1er trimestre\"")
        assertThat(ChartYaml.text("yes")).isEqualTo("\"yes\"")
        assertThat(ChartYaml.text("#22408A")).isEqualTo("\"#22408A\"")
        assertThat(ChartYaml.text("a, b")).isEqualTo("\"a, b\"")
        assertThat(ChartYaml.text("dice \"hola\"")).isEqualTo("\"dice \\\"hola\\\"\"")
        assertThat(ChartYaml.text("")).isEqualTo("\"\"")
    }

    /** Not YAML, a key the server doesn't know, or a shape the form can't show: the editor offers the text only. */
    @Test
    fun `a file the form can't show is text only`() {
        assertThat(ChartFile.read("title: [unclosed").isFailure).isTrue()
        assertThat(ChartFile.read("- just\n- a list").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\ntitle: b").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\nnotes_for_ari: x").isFailure).isTrue()
        assertThat(ChartFile.read("title: {a: 1}").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\ndata: [1, 2]").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\ndata:\n  columns: [a, b]\n  rows: [1, 2]").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\nsource:\n  records: payment\n  colour: red").isFailure).isTrue()
    }

    /** The Notes API's documented example, comments and all. */
    @Test
    fun `the documented example reads`() {
        val documented = """
            title: Ventas por mes
            type: bar            # bar | line | area | pie | donut | stacked | kpi | table
            unit: MXN            # optional
            caption: Sin IVA     # optional
            data:
              columns: [Mes, Ventas]          # the labels' column, then one per series (at most 8)
              rows:
                - [2026-05, 12000]
                - [2026-06, 15400]
            series: [Ventas]     # optional: only these columns
            colors: ["#22408A"]  # optional: these first, then the workspace's palette
        """.trimIndent() + "\n"
        val file = ChartFile.read(documented).getOrThrow()
        assertThat(file.form.caption).isEqualTo("Sin IVA")
        assertThat(file.form.data.rows).containsExactly(listOf("2026-05", "12000"), listOf("2026-06", "15400")).inOrder()
        assertThat(file.write(file.form)).isEqualTo(documented)
        assertThat(ChartChecks.check(file.form, documented)).isNull()
    }

    /** As Ari writes it (PyYAML: lists not indented, single quotes) and with a list spread over several lines. */
    @Test
    fun `PyYAML's style and block lists read`() {
        val pyyaml = "title: Cobros\ntype: pie\ndata:\n  columns:\n  - Método\n  - Total\n  rows:\n  - ['tarjeta', 1200.5]\n  - [efectivo, 800]\n"
        val form = ChartFile.read(pyyaml).getOrThrow().form
        assertThat(form.data.columns).containsExactly("Método", "Total").inOrder()
        assertThat(form.data.rows).containsExactly(listOf("tarjeta", "1200.5"), listOf("efectivo", "800")).inOrder()
        val file = ChartFile.read(pyyaml).getOrThrow()
        assertThat(file.write(file.form)).isEqualTo(pyyaml)
    }

    /** Rows pasted from a spreadsheet: a first line without numbers names the columns. */
    @Test
    fun `pasted rows and their header`() {
        val withHeader = co.artise.android.notes.impl.ui.charteditor.ChartPaste.table("Mes\tVentas\n2026-05\t12000\r\n2026-06\t15,400\n")
        assertThat(withHeader?.columns).containsExactly("Mes", "Ventas").inOrder()
        assertThat(withHeader?.rows).containsExactly(listOf("2026-05", "12000"), listOf("2026-06", "15,400")).inOrder()
        val noHeader = co.artise.android.notes.impl.ui.charteditor.ChartPaste.table("Mayo\t12000\nJunio\t15400")
        assertThat(noHeader?.columns).isNull()
        assertThat(noHeader?.rows).hasSize(2)
        assertThat(co.artise.android.notes.impl.ui.charteditor.ChartPaste.table("  \n")).isNull()
    }

    /** Numbers in the person's own format are saved plain: `1,234.50` (es-MX) and `1.234,50` (pt-BR) are both `1234.5`. */
    @Test
    fun `numbers typed in the person's format are saved plain`() {
        val mx = Locale.forLanguageTag("es-MX")
        val br = Locale.forLanguageTag("pt-BR")
        assertThat(ChartNumbers.toFile("1,234.50", mx)).isEqualTo("1234.5")
        assertThat(ChartNumbers.toFile("1.234,50", br)).isEqualTo("1234.5")
        assertThat(ChartNumbers.toFile("1234,5", br)).isEqualTo("1234.5")
        assertThat(ChartNumbers.toFile("1234.5", br)).isEqualTo("1234.5")
        assertThat(ChartNumbers.toFile("$ 12,000", mx)).isEqualTo("12000")
        assertThat(ChartNumbers.toFile("-3", mx)).isEqualTo("-3")
        assertThat(ChartNumbers.toFile("mucho", mx)).isNull()
        assertThat(ChartNumbers.toFile("", mx)).isNull()
        assertThat(ChartNumbers.forEditing("1234.5", br)).isEqualTo("1234,5")
        assertThat(ChartNumbers.forEditing("1234.5", mx)).isEqualTo("1234.5")
        assertThat(ChartNumbers.forEditing("2026-05", br)).isEqualTo("2026-05")
    }

    /** The server's rules, checked on the phone in its order; the first broken one is what the person sees. */
    @Test
    fun `local checks catch what the server would refuse`() {
        val form = ChartFile.read(ventas).getOrThrow().form.copy(series = emptyList())
        fun check(f: ChartForm) = ChartChecks.check(f, "x")
        assertThat(check(form)).isNull()
        assertThat(ChartChecks.check(form, "x".repeat(ChartChecks.MAX_BYTES))).isEqualTo(ChartCheck.TooBig)
        assertThat(check(form.copy(type = "radar"))).isEqualTo(ChartCheck.Type)
        assertThat(check(form.copy(title = "x".repeat(121)))).isEqualTo(ChartCheck.TitleTooLong)
        assertThat(check(form.copy(title = " "))).isEqualTo(ChartCheck.Title)
        assertThat(check(form.copy(compare = "previous"))).isEqualTo(ChartCheck.Compare)
        assertThat(
            check(form.copy(type = "kpi", compare = "previous", data = form.data.copy(columns = listOf("Mes", "Ventas"), rows = listOf(listOf("a", "1")))))
        ).isNull()
        assertThat(check(form.copy(colors = listOf("#22408A", "red")))).isEqualTo(ChartCheck.Color("red"))
        assertThat(check(form.copy(usesSource = true, source = ChartSource(records = "payment")))).isEqualTo(ChartCheck.Source)
        assertThat(check(form.copy(data = ChartData(listOf("Mes"), emptyList())))).isEqualTo(ChartCheck.Columns)
        assertThat(check(form.copy(data = form.data.copy(columns = listOf("Mes", "Ventas", "Ventas"))))).isEqualTo(ChartCheck.Names)
        assertThat(check(form.copy(data = form.data.copy(rows = emptyList())))).isEqualTo(ChartCheck.NoRows)
        assertThat(check(form.copy(data = form.data.copy(rows = List(501) { listOf("a", "1", "2") })))).isEqualTo(ChartCheck.TooManyRows)
        assertThat(check(form.copy(data = form.data.copy(rows = listOf(listOf("a", "1")))))).isEqualTo(ChartCheck.RowLength(1, 3))
        val nine = ChartData((0..9).map { "c$it" }, listOf((0..9).map { "1" }))
        assertThat(check(form.copy(data = nine))).isEqualTo(ChartCheck.TooManySeries)
        assertThat(check(form.copy(series = listOf("Costos")))).isEqualTo(ChartCheck.UnknownSeries("Costos"))
        assertThat(check(form.copy(type = "pie"))).isEqualTo(ChartCheck.OneSeries)
        assertThat(check(form.copy(data = form.data.copy(rows = listOf(listOf("2026-05", "mucho", "1"))))))
            .isEqualTo(ChartCheck.NotANumber(1, "Ventas", "mucho"))
        val pie = form.copy(type = "donut", series = listOf("Ventas"), data = form.data.copy(rows = listOf(listOf("a", "-1", "0"))))
        assertThat(check(pie)).isEqualTo(ChartCheck.Negative)
    }
}
