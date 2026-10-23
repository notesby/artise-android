/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.dashboard.DashboardBlock
import co.artise.android.notes.impl.dashboard.DashboardProblem
import co.artise.android.notes.impl.dashboard.DashboardText
import co.artise.android.notes.impl.dashboard.DashboardTheme
import co.artise.android.notes.impl.ui.card.formatted
import co.artise.android.notes.impl.ui.note.photoRequest
import co.artise.android.workspace.api.LocalWorkspaceBranding
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A dashboard laid out as the server draws it: its text formatted (a tap edits it), each chart's picture (a tap opens
 * the chart, a long press takes it out), and each `::: columns` block side by side, with buttons to reorder. What the
 * server would complain about shows where it is.
 */
@Composable
internal fun DashboardLayout(dashboard: DashboardEditing, eventSink: (NoteEditorEvent) -> Unit, modifier: Modifier = Modifier) {
    var removing by remember { mutableStateOf<DashboardBlock.Chart?>(null) }
    val problemsByLine = remember(dashboard.problems) { dashboard.problems.filter { it.line != null }.groupBy { it.line } }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Problems of the whole note, and those on a `:::` line (not a block of its own), go first.
        val general = dashboard.problems.filter { it !is DashboardProblem.MissingChart }
        items(general) { ProblemText(it) }
        items(dashboard.blocks, key = { "${it::class.simpleName}:${it.line}" }) { block ->
            when (block) {
                is DashboardBlock.Text -> TextBlock(block, onClick = { eventSink(NoteEditorEvent.EditDashboardText(block.line)) })
                is DashboardBlock.Chart -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartTile(block, dashboard.previews[block.path], eventSink, onLongClick = { removing = block })
                    problemsByLine[block.line].orEmpty().forEach { ProblemText(it) }
                }
                is DashboardBlock.Columns -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.charts.forEachIndexed { index, chart ->
                            Column(modifier = Modifier.size(width = COLUMN_TILE_WIDTH, height = COLUMN_TILE_HEIGHT)) {
                                ChartTile(chart, dashboard.previews[chart.path], eventSink, onLongClick = { removing = chart })
                                MoveButtons(
                                    canMoveBack = index > 0,
                                    canMoveOn = index < block.charts.lastIndex,
                                    onMove = { delta -> eventSink(NoteEditorEvent.MoveDashboardChart(chart.line, delta)) },
                                )
                            }
                        }
                    }
                    block.charts.flatMap { problemsByLine[it.line].orEmpty() }.forEach { ProblemText(it) }
                }
            }
        }
    }
    removing?.let { chart ->
        ConfirmationDialog(
            title = stringResource(R.string.screen_notes_dashboard_remove_chart_title),
            content = stringResource(R.string.screen_notes_dashboard_remove_chart_body),
            submitText = stringResource(CommonStrings.action_remove),
            destructiveSubmit = true,
            onSubmitClick = {
                eventSink(NoteEditorEvent.RemoveDashboardLine(chart.line))
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

/** The text between the charts, formatted as it will read; a tap edits it in the Markdown text. */
@Composable
private fun TextBlock(block: DashboardBlock.Text, onClick: () -> Unit) {
    val styles = livePreviewStyles()
    val shown = remember(block.text, styles) { formatted(block.text, styles) }
    Text(
        text = shown,
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.a11y_notes_dashboard_edit_text), onClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartTile(chart: DashboardBlock.Chart, preview: NotesPreview?, eventSink: (NoteEditorEvent) -> Unit, onLongClick: () -> Unit) {
    val dark = !ElementTheme.isLightTheme
    val sink by rememberUpdatedState(eventSink)
    LaunchedEffect(chart.path, dark) { sink(NoteEditorEvent.LoadChartPreview(chart.path, dark)) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CHART_RATIO)
            .clip(shape)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .combinedClickable(
                onClickLabel = stringResource(R.string.a11y_notes_card_open, chart.ref),
                onLongClickLabel = stringResource(R.string.screen_notes_dashboard_remove_chart_title),
                onClick = { eventSink(NoteEditorEvent.OpenChart(chart.path)) },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (preview is NotesPreview.Image) {
            AsyncImage(
                model = photoRequest(preview.file.path),
                contentDescription = chart.ref,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                Icon(imageVector = CompoundIcons.Chart(), contentDescription = null, tint = ElementTheme.colors.iconTertiary, modifier = Modifier.size(32.dp))
                Text(
                    chart.ref,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MoveButtons(canMoveBack: Boolean, canMoveOn: Boolean, onMove: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveBack) {
            Icon(imageVector = CompoundIcons.ArrowLeft(), contentDescription = stringResource(R.string.a11y_notes_dashboard_move_left))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveOn) {
            Icon(imageVector = CompoundIcons.ArrowRight(), contentDescription = stringResource(R.string.a11y_notes_dashboard_move_right))
        }
    }
}

@Composable
private fun ProblemText(problem: DashboardProblem) {
    Text(text = problemText(problem), style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textCriticalPrimary)
}

@Composable
internal fun problemText(problem: DashboardProblem): String = when (problem) {
    is DashboardProblem.MissingChart -> stringResource(R.string.screen_notes_dashboard_problem_missing, problem.ref)
    is DashboardProblem.UnclosedColumns -> stringResource(R.string.screen_notes_dashboard_problem_unclosed)
    is DashboardProblem.NestedColumns -> stringResource(R.string.screen_notes_dashboard_problem_nested)
    is DashboardProblem.TooManyCharts -> stringResource(R.string.screen_notes_dashboard_problem_too_many, problem.count, DashboardText.MAX_CHARTS)
    is DashboardProblem.BadAccent -> stringResource(R.string.screen_notes_dashboard_problem_accent, problem.value)
    is DashboardProblem.BadPalette -> stringResource(R.string.screen_notes_dashboard_problem_palette, problem.value)
    is DashboardProblem.BadMode -> stringResource(R.string.screen_notes_dashboard_problem_mode, problem.value)
}

/** Under a dashboard's layout: + Chart and Theme. */
@Composable
internal fun DashboardToolbar(dashboard: DashboardEditing, eventSink: (NoteEditorEvent) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var theming by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            text = stringResource(R.string.screen_notes_dashboard_add_chart),
            leadingIcon = IconSource.Vector(CompoundIcons.Plus()),
            onClick = { picking = true },
        )
        OutlinedButton(text = stringResource(R.string.screen_notes_dashboard_theme), onClick = { theming = true })
    }
    if (picking) {
        ChartPicker(
            charts = dashboard.charts,
            onPick = {
                eventSink(NoteEditorEvent.InsertChart(it))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    if (theming) {
        ThemeSheet(theme = dashboard.theme, onChange = { eventSink(NoteEditorEvent.SetDashboardTheme(it)) }, onDismiss = { theming = false })
    }
}

/** The chat's charts, searchable by title; the picked one is added as `![[path]]`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartPicker(charts: ImmutableList<ChartChoice>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, charts) {
        val needle = query.trim().lowercase()
        charts.filter { needle.isEmpty() || needle in it.title.lowercase() || needle in it.path.lowercase() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(modifier = Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.screen_notes_dashboard_pick_chart), style = ElementTheme.typography.fontHeadingSmMedium)
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.screen_notes_dashboard_search_charts),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (charts.isEmpty()) {
                Text(stringResource(R.string.screen_notes_dashboard_no_charts), color = ElementTheme.colors.textSecondary)
            }
            shown.forEach { chart ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(chart.path) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(imageVector = CompoundIcons.Chart(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
                    Column {
                        Text(chart.title, style = ElementTheme.typography.fontBodyLgMedium)
                        Text(chart.path, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

/** The dashboard's own look: its accent, its series' colours (up to 8) and light, dark or the device's. Unset: the workspace's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(theme: DashboardTheme, onChange: (DashboardTheme) -> Unit, onDismiss: () -> Unit) {
    val workspaceColors = LocalWorkspaceBranding.current.chartColors
    var custom by remember { mutableStateOf<((String) -> Unit)?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(modifier = Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.screen_notes_dashboard_theme), style = ElementTheme.typography.fontHeadingSmMedium)
            Text(stringResource(R.string.screen_notes_dashboard_theme_accent), style = ElementTheme.typography.fontBodyMdMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch(color = null, selected = theme.accent.isEmpty()) { onChange(theme.copy(accent = "")) }
                (workspaceColors + theme.accent).filter { it.isNotEmpty() }.distinct().forEach { hex ->
                    Swatch(color = hex, selected = hex.equals(theme.accent, ignoreCase = true)) { onChange(theme.copy(accent = hex)) }
                }
                IconButton(onClick = { custom = { hex -> onChange(theme.copy(accent = hex)) } }) {
                    Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_chart_color_custom))
                }
            }
            Text(stringResource(R.string.screen_notes_dashboard_theme_palette), style = ElementTheme.typography.fontBodyMdMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tapping one of the series' colours takes it out; the rest of the row adds one.
                theme.palette.forEachIndexed { index, hex ->
                    Swatch(
                        color = hex,
                        selected = true
                    ) { onChange(theme.copy(palette = theme.palette.filterIndexed { i, _ -> i != index }.toImmutableList())) }
                }
                if (theme.palette.size < DashboardText.MAX_PALETTE) {
                    workspaceColors.filter { it !in theme.palette }.forEach { hex ->
                        Swatch(color = hex, selected = false) { onChange(theme.copy(palette = (theme.palette + hex).toImmutableList())) }
                    }
                    IconButton(onClick = { custom = { hex -> onChange(theme.copy(palette = (theme.palette + hex).toImmutableList())) } }) {
                        Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_notes_chart_color_custom))
                    }
                }
            }
            Text(stringResource(R.string.screen_notes_dashboard_theme_mode), style = ElementTheme.typography.fontBodyMdMedium)
            val modes = listOf("" to R.string.screen_notes_dashboard_mode_workspace) +
                DashboardText.MODES.zip(
                    listOf(R.string.screen_notes_dashboard_mode_light, R.string.screen_notes_dashboard_mode_dark, R.string.screen_notes_dashboard_mode_auto)
                )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        index = index,
                        count = modes.size,
                        selected = theme.mode == mode,
                        onClick = { onChange(theme.copy(mode = mode)) },
                        text = stringResource(label),
                    )
                }
            }
        }
    }
    custom?.let { apply ->
        TextFieldDialog(
            title = stringResource(R.string.screen_notes_chart_color_custom),
            value = null,
            placeholder = "#22408A",
            onSubmit = {
                apply(it.trim().uppercase())
                custom = null
            },
            onDismissRequest = { custom = null },
            validation = { HEX.matches(it?.trim().orEmpty()) },
        )
    }
}

/** A colour to choose; `null` is the workspace's own (marked "A"). */
@Composable
private fun Swatch(color: String?, selected: Boolean, onClick: () -> Unit) {
    val fill = color?.takeIf { HEX.matches(it) }?.let { Color(it.toColorInt()) }
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
            .clickable(onClickLabel = color ?: stringResource(R.string.screen_notes_chart_color_auto), role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("A", style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
    }
}

private const val CHART_RATIO = 16f / 9f
private val COLUMN_TILE_WIDTH = 220.dp
private val COLUMN_TILE_HEIGHT = 180.dp
private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
