/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.markdown.NoteLink
import co.artise.android.notes.impl.markdown.NoteMarkdownView
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/**
 * A note as it was at one version. Read-only: checkboxes don't tick, links to other notes don't open (they point to today's
 * notes, not that day's), web links do.
 */
@Composable
fun NoteVersionView(
    state: NoteVersionState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = state.title,
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (state.savedAt.isEmpty()) {
                    stringResource(R.string.screen_notes_version_read_only)
                } else {
                    stringResource(R.string.screen_notes_version_from, state.savedAt)
                },
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (state.content != null) {
                NoteMarkdownView(
                    markdown = state.content,
                    onLinkClick = { link -> if (link is NoteLink.Web) uriHandler.openUri(link.url) },
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                HistoryStatus(
                    isLoading = state.isLoading,
                    failed = state.failed,
                    emptyText = null,
                    onRetry = { state.eventSink(NoteVersionEvent.Retry) },
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NoteVersionViewPreview(@PreviewParameter(NoteVersionStatePreviewParam::class) state: NoteVersionState) = ElementPreview {
    NoteVersionView(state = state, onBackClick = {})
}
