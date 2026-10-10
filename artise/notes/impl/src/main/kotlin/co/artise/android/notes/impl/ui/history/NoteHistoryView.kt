/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import co.artise.android.notes.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/** A note's versions; tapping one shows it, read-only. */
@Composable
fun NoteHistoryView(
    state: NoteHistoryState,
    onBackClick: () -> Unit,
    onVersionClick: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_notes_note_history_title, state.title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
        ) {
            state.days.forEach { day ->
                item(key = "day:" + day.label + day.entries.first().commit) { HistoryDayHeader(day.label) }
                items(day.entries, key = { it.commit }) { entry ->
                    HistoryEntryRow(entry = entry, onClick = { onVersionClick(entry) })
                    HorizontalDivider()
                }
            }
            if (state.isLoading || state.failed || state.days.isEmpty()) {
                item(key = "status") {
                    HistoryStatus(
                        isLoading = state.isLoading,
                        failed = state.failed,
                        emptyText = stringResource(R.string.screen_notes_note_history_empty),
                        onRetry = { state.eventSink(NoteHistoryEvent.Retry) },
                    )
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NoteHistoryViewPreview(@PreviewParameter(NoteHistoryStatePreviewParam::class) state: NoteHistoryState) = ElementPreview {
    NoteHistoryView(state = state, onBackClick = {}, onVersionClick = {})
}
