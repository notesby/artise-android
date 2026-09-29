/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import kotlin.math.hypot
import kotlin.math.min

/** The chat's notes as a map: pinch to zoom, drag to move, tap a note to open it. */
@Composable
fun NotesGraphView(
    state: NotesGraphState,
    onBackClick: () -> Unit,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(titleStr = stringResource(R.string.screen_notes_map), navigationIcon = { BackButton(onClick = onBackClick) }) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val graph = state.graph
            when {
                graph == null -> CircularProgressIndicator()
                graph.nodes.isEmpty() -> Text(
                    text = stringResource(R.string.screen_notes_map_empty),
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
                else -> GraphCanvas(graph, onNoteClick)
            }
        }
    }
}

@Composable
private fun GraphCanvas(graph: NotesGraphModel, onNoteClick: (String) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = ElementTheme.typography.fontBodySmRegular.copy(color = ElementTheme.colors.textSecondary)
    val nodeColor = ElementTheme.colors.bgAccentRest
    val edgeColor = ElementTheme.colors.borderInteractiveSecondary
    val density = LocalDensity.current
    val baseRadius = with(density) { 6.dp.toPx() }
    val tapSlop = with(density) { 24.dp.toPx() }
    val labelWidth = with(density) { 120.dp.roundToPx() }

    // Maps a node's 0..1 position to the screen, with the current zoom and pan.
    fun screen(node: GraphNode, size: Size): Offset {
        val side = min(size.width, size.height)
        val origin = Offset((size.width - side) / 2, (size.height - side) / 2)
        val center = Offset(size.width / 2, size.height / 2)
        val base = origin + Offset(node.x * side, node.y * side)
        return center + (base - center) * scale + pan
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(graph) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                    pan += panChange
                }
            }
            .pointerInput(graph) {
                detectTapGestures { tap ->
                    val size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                    graph.nodes
                        .minByOrNull { node -> screen(node, size).let { hypot(it.x - tap.x, it.y - tap.y) } }
                        ?.takeIf { node -> screen(node, size).let { hypot(it.x - tap.x, it.y - tap.y) } <= tapSlop }
                        ?.let { node -> onNoteClick(node.path) }
                }
            }
    ) {
        for ((a, b) in graph.edges) {
            drawLine(edgeColor, screen(graph.nodes[a], size), screen(graph.nodes[b], size), strokeWidth = 1.5f)
        }
        for (node in graph.nodes) {
            val center = screen(node, size)
            // Busier notes are a little bigger, up to twice the size.
            val radius = baseRadius * (1f + min(node.degree, 5) / 5f)
            drawCircle(nodeColor, radius, center)
            val label = textMeasurer.measure(
                text = node.name,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = labelWidth),
            )
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 2f))
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NotesGraphViewPreview(@PreviewParameter(NotesGraphStatePreviewParam::class) state: NotesGraphState) = ElementPreview {
    NotesGraphView(state = state, onBackClick = {}, onNoteClick = {})
}
