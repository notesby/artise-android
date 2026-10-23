/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.charteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.RecordType
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.chart.ChartCheck
import co.artise.android.notes.impl.chart.ChartSource
import co.artise.android.notes.impl.chart.ChartType
import co.artise.android.workspace.api.LocalWorkspaceBranding
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Switch
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartEditorView(
    state: ChartEditorState,
    onBackClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isDone) {
        val done by rememberUpdatedState(onDone)
        LaunchedEffect(Unit) { done() }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.screen_notes_new_chart else R.string.screen_notes_chart_editor_title)) },
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            text = stringResource(CommonStrings.action_save),
                            enabled = state.canSave,
                            onClick = { state.eventSink(ChartEditorEvent.Save) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ChartPreview(state) }
            item { Problems(state) }
            item {
                SwitchRow(
                    text = stringResource(R.string.screen_notes_chart_show_file),
                    checked = state.showFile,
                    onCheckedChange = { state.eventSink(ChartEditorEvent.ShowFile(it)) },
                )
            }
            if (state.showFile) {
                item { FileText(state) }
            } else {
                item { FormFields(state) }
                item { Colors(state) }
                item { DataOrSource(state) }
                if (state.form.usesSource) {
                    item { SourceForm(state.form.source, state.recordTypes, onChange = { state.eventSink(ChartEditorEvent.SetSource(it)) }) }
                } else {
                    item { DataGrid(state) }
                }
            }
        }
    }
}

/** The server's drawing of the unsaved chart, refreshed a moment after typing stops. */
@Composable
private fun ChartPreview(state: ChartEditorState) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_RATIO)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondary, shape),
        contentAlignment = Alignment.Center,
    ) {
        val preview = state.preview
        if (preview != null) {
            val context = LocalContext.current
            val request = remember(preview) { ImageRequest.Builder(context).data(preview.bytes).build() }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.a11y_notes_chart_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(imageVector = CompoundIcons.Chart(), contentDescription = null, tint = ElementTheme.colors.iconTertiary, modifier = Modifier.size(40.dp))
        }
        if (state.isPreviewing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun Problems(state: ChartEditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.check?.let { Text(text = checkText(it), style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textCriticalPrimary) }
        // The server's own words, as they come.
        state.serverMessage?.let { Text(text = it, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textCriticalPrimary) }
        if (state.fileUnreadable) {
            Text(
                text = stringResource(R.string.screen_notes_chart_file_unreadable),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FileText(state: ChartEditorState) {
    TextField(
        value = state.fileText,
        onValueChange = { state.eventSink(ChartEditorEvent.SetFileText(it)) },
        minLines = FILE_MIN_LINES,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun FormFields(state: ChartEditorState) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = form.title,
            onValueChange = { state.eventSink(ChartEditorEvent.SetTitle(it)) },
            label = stringResource(R.string.screen_notes_chart_field_title),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TypePicker(selected = form.type, onSelect = { state.eventSink(ChartEditorEvent.SetType(it)) })
        TextField(
            value = form.unit,
            onValueChange = { state.eventSink(ChartEditorEvent.SetUnit(it)) },
            label = stringResource(R.string.screen_notes_chart_field_unit),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = form.caption,
            onValueChange = { state.eventSink(ChartEditorEvent.SetCaption(it)) },
            label = stringResource(R.string.screen_notes_chart_field_caption),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.type == ChartType.KPI.value) {
            SwitchRow(
                text = stringResource(R.string.screen_notes_chart_field_compare),
                checked = form.compare.isNotEmpty(),
                onCheckedChange = { state.eventSink(ChartEditorEvent.SetCompare(it)) },
            )
        }
    }
}

/** The chart types as a row of choices, each with its name. */
@Composable
private fun TypePicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.screen_notes_chart_field_type),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartType.entries.forEach { type ->
                val isSelected = type.value == selected
                val shape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .border(1.dp, if (isSelected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary, shape)
                        .background(if (isSelected) ElementTheme.colors.bgSubtlePrimary else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onSelect(type.value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(imageVector = iconOf(type), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(typeName(type), style = ElementTheme.typography.fontBodyMdMedium)
                }
            }
        }
    }
}

/** One colour per series: the workspace's palette, or a colour of one's own; none means the palette's own order. */
@Composable
private fun Colors(state: ChartEditorState) {
    val series = state.form.drawnSeries
    if (series.isEmpty()) return
    val palette = LocalWorkspaceBranding.current.chartColors
    var customFor by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.screen_notes_chart_colors), style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
        series.take(MAX_COLORS).forEachIndexed { index, name ->
            val current = state.form.colors.getOrNull(index)
            // The file's colours go in order: one can be set once the one before it is.
            val canSet = index <= state.form.colors.size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, modifier = Modifier.width(96.dp), maxLines = 1)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorDot(color = null, selected = current == null, enabled = true) { state.eventSink(ChartEditorEvent.SetColor(index, null)) }
                    palette.forEach { hex ->
                        ColorDot(color = hex, selected = current.equals(hex, ignoreCase = true), enabled = canSet) {
                            state.eventSink(ChartEditorEvent.SetColor(index, hex))
                        }
                    }
                    IconButton(onClick = { customFor = index }, enabled = canSet) {
                        Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_chart_color_custom))
                    }
                }
            }
        }
    }
    customFor?.let { index ->
        TextFieldDialog(
            title = stringResource(R.string.screen_notes_chart_color_custom),
            value = state.form.colors.getOrNull(index),
            placeholder = "#22408A",
            onSubmit = {
                state.eventSink(ChartEditorEvent.SetColor(index, it.trim().uppercase()))
                customFor = null
            },
            onDismissRequest = { customFor = null },
            validation = { HEX.matches(it?.trim().orEmpty()) },
        )
    }
}

@Composable
private fun ColorDot(color: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill = color?.let { Color(android.graphics.Color.parseColor(it)) }
    val label = color ?: stringResource(R.string.screen_notes_chart_color_auto)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill ?: ElementTheme.colors.bgSubtleSecondary)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                CircleShape
            )
            .clickable(enabled = enabled, onClickLabel = label, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (fill == null) Text("A", style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
    }
}

@Composable
private fun DataOrSource(state: ChartEditorState) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            index = 0,
            count = 2,
            selected = !state.form.usesSource,
            onClick = { state.eventSink(ChartEditorEvent.UseSource(false)) },
            text = stringResource(R.string.screen_notes_chart_tab_data),
        )
        SegmentedButton(
            index = 1,
            count = 2,
            selected = state.form.usesSource,
            onClick = { state.eventSink(ChartEditorEvent.UseSource(true)) },
            text = stringResource(R.string.screen_notes_chart_tab_source),
        )
    }
}

/** The frozen numbers as a grid: the first column the labels, then one per series; numbers in the person's own format. */
@Composable
private fun DataGrid(state: ChartEditorState) {
    val columns = state.form.data.columns
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                columns.forEachIndexed { c, name ->
                    GridCell(value = name, isHeader = true, onValueChange = { state.eventSink(ChartEditorEvent.SetColumnName(c, it)) })
                    if (c > 0) {
                        IconButton(onClick = { state.eventSink(ChartEditorEvent.RemoveColumn(c)) }) {
                            Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(R.string.a11y_notes_chart_remove_column, name))
                        }
                    }
                }
            }
            state.grid.forEachIndexed { r, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { c, cell ->
                        GridCell(value = cell, isHeader = false, onValueChange = { state.eventSink(ChartEditorEvent.SetCell(r, c, it)) })
                        if (c > 0) Box(modifier = Modifier.width(48.dp))
                    }
                    IconButton(onClick = { state.eventSink(ChartEditorEvent.RemoveRow(r)) }) {
                        Icon(imageVector = CompoundIcons.Delete(), contentDescription = stringResource(R.string.a11y_notes_chart_remove_row, r + 1))
                    }
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(text = stringResource(R.string.screen_notes_chart_add_row), onClick = { state.eventSink(ChartEditorEvent.AddRow) })
            OutlinedButton(text = stringResource(R.string.screen_notes_chart_add_column), onClick = { state.eventSink(ChartEditorEvent.AddColumn) })
            OutlinedButton(
                text = stringResource(R.string.screen_notes_chart_paste),
                onClick = {
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        // Rows from a spreadsheet go in below the rows there are.
                        if (!text.isNullOrBlank()) state.eventSink(ChartEditorEvent.Paste(row = state.grid.size, column = 0, text = text))
                    }
                },
            )
        }
    }
}

@Composable
private fun GridCell(value: String, isHeader: Boolean, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.width(CELL_WIDTH).padding(end = 4.dp),
        placeholder = if (isHeader) null else "",
    )
}

/** A sourced chart's pickers: the record type, a filter, the period, what goes along the bottom, what to count, and series by. */
@Composable
private fun SourceForm(source: ChartSource, recordTypes: ImmutableList<RecordType>, onChange: (ChartSource) -> Unit) {
    if (recordTypes.isEmpty() && source.records.isBlank()) {
        Text(stringResource(R.string.screen_notes_chart_no_records), color = ElementTheme.colors.textSecondary)
        return
    }
    val type = recordTypes.firstOrNull { it.type == source.records }
    val fields = type?.fields.orEmpty().toImmutableList()
    val none = stringResource(R.string.screen_notes_chart_source_none)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Picker(
            label = stringResource(R.string.screen_notes_chart_source_records),
            value = source.records,
            options = (recordTypes.map { it.type } + source.records).filter { it.isNotBlank() }.distinct().map { it to it }.toImmutableList(),
            onSelect = { onChange(source.copy(records = it, where = emptyList(), group = "")) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val (whereField, whereValue) = source.where.firstOrNull() ?: "".to("")
            Box(modifier = Modifier.weight(1f)) {
                Picker(
                    label = stringResource(R.string.screen_notes_chart_source_where),
                    value = whereField,
                    options = (listOf("" to none) + fields.map { it to it }).toImmutableList(),
                    onSelect = { field ->
                        onChange(
                        source.copy(where = if (field.isEmpty()) emptyList() else listOf(field to whereValue) + source.where.drop(1))
                    )
                    },
                )
            }
            if (whereField.isNotEmpty()) {
                TextField(
                    value = whereValue,
                    onValueChange = { value -> onChange(source.copy(where = listOf(whereField to value) + source.where.drop(1))) },
                    label = stringResource(R.string.screen_notes_chart_source_where_value),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Picker(
            label = stringResource(R.string.screen_notes_chart_source_period),
            value = source.period,
            // A period the file has that isn't a preset ("2026-Q3", "from 2026-05-01") stays on offer.
            options = periodOptions().let { presets ->
                val isPreset = source.period.isBlank() || presets.any { (preset, _) -> preset == source.period }
                if (isPreset) presets else presets + (source.period to source.period)
            }.toImmutableList(),
            onSelect = { onChange(source.copy(period = it)) },
        )
        Picker(
            label = stringResource(R.string.screen_notes_chart_source_x),
            value = source.x,
            options = (xOptions() + fields.map { it to it }).toImmutableList(),
            onSelect = { onChange(source.copy(x = it)) },
        )
        Picker(
            label = stringResource(R.string.screen_notes_chart_source_y),
            value = source.y,
            options = yOptions(fields, type?.amountField).toImmutableList(),
            onSelect = { onChange(source.copy(y = it)) },
        )
        Picker(
            label = stringResource(R.string.screen_notes_chart_source_group),
            value = source.group,
            options = (listOf("" to none) + fields.map { it to it }).toImmutableList(),
            onSelect = { onChange(source.copy(group = it)) },
        )
    }
}

/** A labelled choice: shows the chosen option's name, and the options in a menu. [options] are (value, name). */
@Composable
private fun Picker(label: String, value: String, options: ImmutableList<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = label,
            singleLine = true,
            trailingIcon = { Icon(imageVector = CompoundIcons.ChevronDown(), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Over the field, so a tap anywhere on it opens the menu.
        Box(modifier = Modifier.matchParentSize().clickable(role = Role.DropdownList) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(optionValue)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun periodOptions(): List<Pair<String, String>> = listOf(
    "last 7 days" to stringResource(R.string.screen_notes_chart_period_7_days),
    "last 30 days" to stringResource(R.string.screen_notes_chart_period_30_days),
    "last 6 months" to stringResource(R.string.screen_notes_chart_period_6_months),
    "last 12 months" to stringResource(R.string.screen_notes_chart_period_12_months),
    "this month" to stringResource(R.string.screen_notes_chart_period_this_month),
    "this quarter" to stringResource(R.string.screen_notes_chart_period_this_quarter),
    "this year" to stringResource(R.string.screen_notes_chart_period_this_year),
)

@Composable
private fun xOptions(): List<Pair<String, String>> = listOf(
    "day" to stringResource(R.string.screen_notes_chart_x_day),
    "week" to stringResource(R.string.screen_notes_chart_x_week),
    "month" to stringResource(R.string.screen_notes_chart_x_month),
    "quarter" to stringResource(R.string.screen_notes_chart_x_quarter),
    "year" to stringResource(R.string.screen_notes_chart_x_year),
)

/** count, then sum/avg/min/max of each field, the record type's amount first. */
@Composable
private fun yOptions(fields: ImmutableList<String>, amountField: String?): List<Pair<String, String>> {
    val ordered = listOfNotNull(amountField?.takeIf { it in fields }) + fields.filter { it != amountField }
    return listOf("count" to stringResource(R.string.screen_notes_chart_y_count)) + ordered.flatMap { field ->
        listOf(
            "sum($field)" to stringResource(R.string.screen_notes_chart_y_sum, field),
            "avg($field)" to stringResource(R.string.screen_notes_chart_y_avg, field),
            "min($field)" to stringResource(R.string.screen_notes_chart_y_min, field),
            "max($field)" to stringResource(R.string.screen_notes_chart_y_max, field),
        )
    }
}

@Composable
internal fun checkText(check: ChartCheck): String = when (check) {
    ChartCheck.Title -> stringResource(R.string.screen_notes_chart_check_title)
    ChartCheck.Columns -> stringResource(R.string.screen_notes_chart_check_columns)
    ChartCheck.Names -> stringResource(R.string.screen_notes_chart_check_names)
    is ChartCheck.NotANumber -> stringResource(R.string.screen_notes_chart_check_number, check.row, check.column, check.value)
    ChartCheck.TooManySeries -> stringResource(R.string.screen_notes_chart_check_series)
    ChartCheck.TooManyRows -> stringResource(R.string.screen_notes_chart_check_rows)
    ChartCheck.OneSeries -> stringResource(R.string.screen_notes_chart_check_one_series)
    ChartCheck.Negative -> stringResource(R.string.screen_notes_chart_check_negative)
    ChartCheck.Source -> stringResource(R.string.screen_notes_chart_check_source)
    ChartCheck.TooBig -> stringResource(R.string.screen_notes_chart_check_too_big)
}

@Composable
private fun typeName(type: ChartType): String = stringResource(
    when (type) {
        ChartType.BAR -> R.string.screen_notes_chart_type_bar
        ChartType.LINE -> R.string.screen_notes_chart_type_line
        ChartType.AREA -> R.string.screen_notes_chart_type_area
        ChartType.PIE -> R.string.screen_notes_chart_type_pie
        ChartType.DONUT -> R.string.screen_notes_chart_type_donut
        ChartType.STACKED -> R.string.screen_notes_chart_type_stacked
        ChartType.KPI -> R.string.screen_notes_chart_type_kpi
        ChartType.TABLE -> R.string.screen_notes_chart_type_table
    },
)

@Composable
private fun iconOf(type: ChartType) = when (type) {
    ChartType.TABLE -> CompoundIcons.Grid()
    ChartType.KPI -> CompoundIcons.Info()
    else -> CompoundIcons.Chart()
}

private const val PREVIEW_RATIO = 16f / 9f
private const val FILE_MIN_LINES = 12
private const val MAX_COLORS = 8
private val CELL_WIDTH = 120.dp
private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

@PreviewsDayNight
@Composable
internal fun ChartEditorViewPreview(@PreviewParameter(ChartEditorStatePreviewParam::class) state: ChartEditorState) = ElementPreview {
    ChartEditorView(state = state, onBackClick = {}, onDone = {})
}
