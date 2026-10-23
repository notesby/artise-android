/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.share.ShareAction
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf

private val chartFormats = persistentListOf(ExportFormat.PNG, ExportFormat.PDF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesChartView(
    state: NotesChartState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = onEditClick) {
                            Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_chart_edit))
                        }
                    }
                    ShareAction(state = state.share, formats = chartFormats, title = state.title)
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { state.eventSink(NotesChartEvent.Refresh) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            ChartBody(state)
        }
    }
}

@Composable
private fun ChartBody(state: NotesChartState) {
    val chart = state.chart
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            chart != null -> {
                val context = LocalContext.current
                // The server's SVG, drawn at the screen's width (the app's image loader reads SVG).
                val request = remember(chart.svg) { ImageRequest.Builder(context).data(chart.svg.toByteArray()).build() }
                AsyncImage(
                    model = request,
                    contentDescription = state.altText,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChartDetails(state)
            }
            state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Unit
        }
        state.problem?.let { problem -> ProblemText(problem, onRetry = { state.eventSink(NotesChartEvent.Refresh) }) }
    }
}

@Composable
private fun ChartDetails(state: NotesChartState) {
    val spec = state.chart?.spec ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (spec.caption.isNotBlank()) {
            Text(text = spec.caption, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textPrimary)
        }
        state.chart.asOf.takeIf { it.isNotBlank() }?.let { asOf ->
            Text(
                text = stringResource(R.string.screen_notes_chart_as_of, asOf),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ProblemText(problem: ChartProblem, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when (problem) {
                ChartProblem.LoadFailed -> stringResource(R.string.screen_notes_chart_load_failed)
                // The server's own sentence: what's wrong in the file.
                is ChartProblem.BadChart -> problem.message.ifBlank { stringResource(R.string.screen_notes_chart_load_failed) }
            },
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (problem == ChartProblem.LoadFailed) {
            OutlinedButton(text = stringResource(CommonStrings.action_retry), onClick = onRetry, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NotesChartViewPreview(@PreviewParameter(NotesChartStatePreviewParam::class) state: NotesChartState) = ElementPreview {
    NotesChartView(state = state, onBackClick = {}, onEditClick = {})
}
