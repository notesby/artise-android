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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.ExportFormat
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.share.ShareAction
import co.artise.android.notes.impl.ui.note.photoRequest
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
                            Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_edit))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.title,
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        when (val picture = state.picture) {
            is NotesPreview.Image -> AsyncImage(
                model = photoRequest(picture.file.path),
                contentDescription = state.altText,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.failed) {
            Text(
                text = stringResource(R.string.screen_notes_chart_failed),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val spec = state.chart?.spec
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (spec != null && spec.caption.isNotBlank()) {
                Text(text = spec.caption, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textPrimary)
            }
            state.chart?.asOf?.takeIf { it.isNotBlank() }?.let { asOf ->
                Text(
                    text = stringResource(R.string.screen_notes_chart_as_of, localDay(asOf)),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** "2026-10-22" as the person writes a day ("22 de octubre de 2026"); anything else as it is. */
internal fun localDay(day: String, locale: Locale = Locale.getDefault()): String =
    tryOrNull { LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)) } ?: day

@PreviewsDayNight
@Composable
internal fun NotesChartViewPreview(@PreviewParameter(NotesChartStatePreviewParam::class) state: NotesChartState) = ElementPreview {
    NotesChartView(state = state, onBackClick = {}, onEditClick = {})
}
