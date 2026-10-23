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
        notes_for_ari: keep it   # not a key the server knows, kept anyway
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

    /** Nothing changed: the very same text, comments, unknown keys and all. */
    @Test
    fun `an unchanged form writes the file as it was`() {
        val file = ChartFile.read(ventas).getOrThrow()
        assertThat(file.write(file.form)).isEqualTo(ventas)
    }

    /** One field changed: only its line is written again; the key order, comments and unknown keys stay. */
    @Test
    fun `a change rewrites only its key`() {
        val file = ChartFile.read(ventas).getOrThrow()
        val written = file.write(file.form.copy(title = "Ventas: mayo y junio", caption = "Sin IVA"))
        assertThat(written).isEqualTo(
            ventas
                .replace("title: Ventas por mes", "title: \"Ventas: mayo y junio\"")
                .replace("unit: MXN\n", "unit: MXN\ncaption: Sin IVA\n"),
        )
        // And it reads back the same
        assertThat(ChartFile.read(written).getOrThrow().form.title).isEqualTo("Ventas: mayo y junio")
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
                - [2026-07, 1234.5, "no"]
                - ["2026-08-01", -3, 7]
            """.trimIndent(),
        )
        assertThat(written).doesNotContain("series:")
        assertThat(written).contains("notes_for_ari: keep it")
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
        // In its place: after unit, before series
        assertThat(written.indexOf("source:")).isGreaterThan(written.indexOf("unit:"))
        assertThat(written.indexOf("source:")).isLessThan(written.indexOf("series:"))
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
        assertThat(ChartYaml.text("yes")).isEqualTo("\"yes\"")
        assertThat(ChartYaml.text("#22408A")).isEqualTo("\"#22408A\"")
        assertThat(ChartYaml.text("a, b")).isEqualTo("\"a, b\"")
        assertThat(ChartYaml.text("dice \"hola\"")).isEqualTo("\"dice \\\"hola\\\"\"")
        assertThat(ChartYaml.text("")).isEqualTo("\"\"")
    }

    @Test
    fun `a file that isn't a chart's YAML can't be read into the form`() {
        assertThat(ChartFile.read("title: [unclosed").isFailure).isTrue()
        assertThat(ChartFile.read("- just\n- a list").isFailure).isTrue()
        assertThat(ChartFile.read("title: a\ntitle: b").isFailure).isTrue()
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

    /** The server's rules, checked on the phone; the first broken one is what the person sees. */
    @Test
    fun `local checks catch what the server would refuse`() {
        val form = ChartFile.read(ventas).getOrThrow().form.copy(series = emptyList())
        fun check(f: ChartForm) = ChartChecks.check(f, "x")
        assertThat(check(form)).isNull()
        assertThat(check(form.copy(title = " "))).isEqualTo(ChartCheck.Title)
        assertThat(check(form.copy(data = ChartData(listOf("Mes"), emptyList())))).isEqualTo(ChartCheck.Columns)
        assertThat(check(form.copy(data = form.data.copy(columns = listOf("Mes", "Ventas", "Ventas"))))).isEqualTo(ChartCheck.Names)
        assertThat(check(form.copy(data = form.data.copy(rows = listOf(listOf("2026-05", "mucho", "1"))))))
            .isEqualTo(ChartCheck.NotANumber(1, "Ventas", "mucho"))
        val nine = ChartData((0..9).map { "c$it" }, listOf((0..9).map { "1" }))
        assertThat(check(form.copy(data = nine))).isEqualTo(ChartCheck.TooManySeries)
        assertThat(check(form.copy(data = form.data.copy(rows = List(501) { listOf("a", "1", "2") })))).isEqualTo(ChartCheck.TooManyRows)
        assertThat(check(form.copy(type = "pie"))).isEqualTo(ChartCheck.OneSeries)
        val pie = form.copy(type = "donut", series = listOf("Ventas"), data = form.data.copy(rows = listOf(listOf("a", "-1", "0"))))
        assertThat(check(pie)).isEqualTo(ChartCheck.Negative)
        assertThat(check(form.copy(usesSource = true, source = ChartSource(records = "payment")))).isEqualTo(ChartCheck.Source)
        assertThat(ChartChecks.check(form, "x".repeat(ChartChecks.MAX_BYTES))).isEqualTo(ChartCheck.TooBig)
    }
}
