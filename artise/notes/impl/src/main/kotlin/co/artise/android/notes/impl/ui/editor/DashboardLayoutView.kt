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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.dashboard.DashboardBlock
import co.artise.android.notes.impl.dashboard.DashboardProblem
import co.artise.android.notes.impl.dashboard.DashboardText
import co.artise.android.notes.impl.dashboard.DashboardTheme
import co.artise.android.notes.impl.share.withAssistant
import co.artise.android.notes.impl.ui.note.photoRequest
import co.artise.android.workspace.api.LocalWorkspaceBranding
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** A dashboard's two views in the editor: its layout (charts drawn) or its Markdown text. */
@Composable
internal fun LayoutOrText(showLayout: Boolean, onShowLayout: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SegmentedButton(
            index = 0,
            count = 2,
            selected = showLayout,
            onClick = { onShowLayout(true) },
            text = stringResource(R.string.screen_notes_dashboard_layout)
        )
        SegmentedButton(
            index = 1,
            count = 2,
            selected = !showLayout,
            onClick = { onShowLayout(false) },
            text = stringResource(R.string.screen_notes_dashboard_text)
        )
    }
}

/**
 * A dashboard laid out as the server draws it, one row per block, each with a handle to drag it: text (a tap edits it),
 * a chart (a tap opens it, a long press offers to take it out), or charts side by side. What the server would complain
 * about shows in the block it's about; the rest on top.
 */
@Composable
internal fun DashboardLayout(dashboard: DashboardEditing, eventSink: (NoteEditorEvent) -> Unit, modifier: Modifier = Modifier) {
    val sink by rememberUpdatedState(eventSink)
    val titles = remember(dashboard.charts) { dashboard.charts.associate { it.path to it.title }.toImmutableMap() }
    // Dragging: which block, how far it has moved, and where each block sits, to know where it lands.
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val tops = remember { mutableStateMapOf<Int, Float>() }
    val heights = remember { mutableStateMapOf<Int, Float>() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        dashboard.problems.filter { problem -> dashboard.blocks.none { it.holds(problem) } }.forEach { ProblemText(it) }
        dashboard.blocks.forEachIndexed { index, block ->
            val isDragged = dragging == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        tops[index] = it.positionInParent().y
                        heights[index] = it.size.height.toFloat()
                    }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .background(ElementTheme.colors.bgCanvasDefault),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BlockRow(block, dashboard.previews, titles, sink)
                    dashboard.problems.filter { block.holds(it) }.forEach { ProblemText(it) }
                }
                Icon(
                    imageVector = CompoundIcons.DragList(),
                    contentDescription = stringResource(R.string.a11y_notes_dashboard_move),
                    tint = ElementTheme.colors.iconSecondary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .pointerInput(index, dashboard.blocks.size) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    val center = (tops[index] ?: 0f) + (heights[index] ?: 0f) / 2 + dragOffset
                                    val to = dashboard.blocks.indices
                                        .firstOrNull { it != index && center < (tops[it] ?: 0f) + (heights[it] ?: 0f) / 2 }
                                        ?: dashboard.blocks.size
                                    sink(NoteEditorEvent.MoveDashboardBlock(index, to))
                                    dragging = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragging = -1
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                },
                            )
                        },
                )
            }
        }
    }
}

/** Whether [problem] is about a line inside this block. */
private fun DashboardBlock.holds(problem: DashboardProblem): Boolean {
    val at = problem.line ?: return false
    return at in line..lastLine
}

@Composable
private fun BlockRow(
    block: DashboardBlock,
    previews: ImmutableMap<String, NotesPreview>,
    titles: ImmutableMap<String, String>,
    eventSink: (NoteEditorEvent) -> Unit,
) {
    when (block) {
        is DashboardBlock.Text -> Text(
            text = DashboardText.firstLines(block.text),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable { eventSink(NoteEditorEvent.EditDashboardText(block.line)) }.padding(vertical = 8.dp),
        )
        is DashboardBlock.Chart -> ChartTile(block, previews[block.path], titles[block.path], eventSink, modifier = Modifier.fillMaxWidth())
        is DashboardBlock.Columns -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.screen_notes_dashboard_columns),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textSecondary
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                block.charts.forEach { chart ->
                    ChartTile(chart, previews[chart.path], titles[chart.path], eventSink, modifier = Modifier.width(COLUMN_TILE_WIDTH))
                }
            }
        }
    }
}

/** A chart's picture (256 px, this screen's light or dark) and title; a tap opens it, a long press offers to take it out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartTile(
    chart: DashboardBlock.Chart,
    preview: NotesPreview?,
    title: String?,
    eventSink: (NoteEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = !ElementTheme.isLightTheme
    val sink by rememberUpdatedState(eventSink)
    LaunchedEffect(chart.path, dark) { sink(NoteEditorEvent.LoadChartPreview(chart.path, dark)) }
    var showMenu by remember { mutableStateOf(false) }
    val name = title ?: chart.ref.substringAfterLast('/').removeSuffix(".chart")
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.a11y_notes_chart, name),
                    onLongClickLabel = stringResource(R.string.screen_notes_dashboard_remove),
                    onClick = { eventSink(NoteEditorEvent.OpenChart(chart.path)) },
                    onLongClick = { showMenu = true },
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(CHART_RATIO).background(ElementTheme.colors.bgSubtleSecondary),
                contentAlignment = Alignment.Center,
            ) {
                if (preview is NotesPreview.Image) {
                    AsyncImage(
                        model = photoRequest(preview.file.path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Icon(
                        imageVector = CompoundIcons.Chart(),
                        contentDescription = null,
                        tint = ElementTheme.colors.iconTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = name,
                style = ElementTheme.typography.fontBodyMdMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.screen_notes_dashboard_remove)) },
                onClick = {
                    showMenu = false
                    eventSink(NoteEditorEvent.RemoveDashboardLine(chart.line))
                },
            )
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
    is DashboardProblem.TooManyCharts -> stringResource(R.string.screen_notes_dashboard_problem_too_many)
    is DashboardProblem.BadAccent -> stringResource(R.string.screen_notes_chart_problem_color, problem.value)
    is DashboardProblem.BadPalette -> stringResource(R.string.screen_notes_dashboard_problem_palette)
    is DashboardProblem.BadMode -> stringResource(R.string.screen_notes_dashboard_problem_mode, problem.value)
}

/** Under a dashboard's layout: Add chart and Theme. */
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
        ThemeSheet(
            theme = dashboard.theme,
            onDone = {
                eventSink(NoteEditorEvent.SetDashboardTheme(it))
                theming = false
            },
            onDismiss = { theming = false },
        )
    }
}

/** The chat's charts, searchable by title; the picked one is added at the end as `![[path]]`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartPicker(charts: ImmutableList<ChartChoice>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, charts) {
        val needle = query.trim().lowercase()
        charts.filter { needle.isEmpty() || needle in it.title.lowercase() }
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
                Text(
                    withAssistant(stringResource(R.string.screen_notes_dashboard_no_charts), LocalWorkspaceBranding.current.assistantName),
                    color = ElementTheme.colors.textSecondary,
                )
            }
            shown.forEach { chart ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(chart.path) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(imageVector = CompoundIcons.Chart(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
                    Text(chart.title, style = ElementTheme.typography.fontBodyLgMedium)
                }
            }
        }
    }
}

/**
 * The dashboard's own look, written on Done as one `theme:` line: its main colour, its series' colours (up to 8), and
 * light, dark or like the device ("like the device" stays unset unless the note already set a mode). "Use the
 * workspace's colours" takes the theme out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(theme: DashboardTheme, onDone: (DashboardTheme) -> Unit, onDismiss: () -> Unit) {
    val workspaceColors = LocalWorkspaceBranding.current.chartColors
    var draft by remember { mutableStateOf(theme) }
    var custom by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var customTitle by remember { mutableIntStateOf(R.string.screen_notes_dashboard_theme_accent) }
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(modifier = Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.screen_notes_dashboard_theme), style = ElementTheme.typography.fontHeadingSmMedium, modifier = Modifier.weight(1f))
                Button(text = stringResource(CommonStrings.action_done), onClick = { onDone(draft) })
            }
            Text(stringResource(R.string.screen_notes_dashboard_theme_accent), style = ElementTheme.typography.fontBodyMdMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (workspaceColors + draft.accent).filter { it.isNotEmpty() }.distinct().forEach { hex ->
                    Swatch(color = hex, selected = hex.equals(draft.accent, ignoreCase = true)) { draft = draft.copy(accent = hex) }
                }
                IconButton(onClick = {
                    customTitle = R.string.screen_notes_dashboard_theme_accent
                    custom = { hex -> draft = draft.copy(accent = hex) }
                }) {
                    Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_dashboard_theme_accent))
                }
            }
            Text(stringResource(R.string.screen_notes_dashboard_theme_palette), style = ElementTheme.typography.fontBodyMdMedium)
            draft.palette.forEachIndexed { index, hex ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch(color = hex, selected = false) {}
                    Text(hex, modifier = Modifier.weight(1f))
                    IconButton(onClick = { draft = draft.copy(palette = draft.palette.filterIndexed { i, _ -> i != index }.toImmutableList()) }) {
                        Icon(imageVector = CompoundIcons.Delete(), contentDescription = stringResource(CommonStrings.action_remove))
                    }
                }
            }
            if (draft.palette.size < DashboardText.MAX_PALETTE) {
                TextButton(
                    text = stringResource(R.string.screen_notes_dashboard_theme_add_color),
                    leadingIcon = IconSource.Vector(CompoundIcons.Plus()),
                    onClick = {
                        customTitle = R.string.screen_notes_dashboard_theme_add_color
                        custom = { hex -> draft = draft.copy(palette = (draft.palette + hex).toImmutableList()) }
                    },
                )
            }
            Text(stringResource(R.string.screen_notes_dashboard_theme_mode), style = ElementTheme.typography.fontBodyMdMedium)
            val modes = listOf(
                "light" to R.string.screen_notes_dashboard_theme_mode_light,
                "dark" to R.string.screen_notes_dashboard_theme_mode_dark,
                "auto" to R.string.screen_notes_dashboard_theme_mode_auto,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    val isAuto = mode == "auto"
                    SegmentedButton(
                        index = index,
                        count = modes.size,
                        selected = draft.mode == mode || isAuto && draft.mode.isEmpty(),
                        // "Like the device" is what an unset mode does: only written when the note had one.
                        onClick = { draft = draft.copy(mode = if (isAuto && theme.mode.isEmpty()) "" else mode) },
                        text = stringResource(label),
                    )
                }
            }
            TextButton(text = stringResource(R.string.screen_notes_dashboard_theme_workspace), onClick = { onDone(DashboardTheme()) })
        }
    }
    custom?.let { apply ->
        TextFieldDialog(
            title = stringResource(customTitle),
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

@Composable
private fun Swatch(color: String, selected: Boolean, onClick: () -> Unit) {
    val fill = color.takeIf { HEX.matches(it) }?.let { Color(it.toColorInt()) } ?: ElementTheme.colors.bgSubtleSecondary
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                CircleShape
            )
            .clickable(onClickLabel = color, role = Role.RadioButton, onClick = onClick),
    )
}

private const val CHART_RATIO = 16f / 9f
private val COLUMN_TILE_WIDTH = 200.dp
private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
