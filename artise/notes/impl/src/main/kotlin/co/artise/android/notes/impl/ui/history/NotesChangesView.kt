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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.note.openDownloadedFile
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/** A chat's change history: days, and in each the changes with who they were for and the files they touched. */
@Composable
fun NotesChangesView(
    state: NotesChangesState,
    onBackClick: () -> Unit,
    onOpenNote: (path: String) -> Unit,
    onOpenNoteHistory: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    state.openFile?.let { request ->
        LaunchedEffect(request) { state.eventSink(NotesChangesEvent.FileOpenHandled(opened = context.openDownloadedFile(request))) }
    }
    val listState = rememberLazyListState()
    // Ask for the next page a few rows before the end, so scrolling doesn't stop.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore, state.days.size) {
        if (nearEnd && state.hasMore && !state.failed) state.eventSink(NotesChangesEvent.LoadMore)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_notes_history_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            state.days.forEach { day ->
                item(key = "day:" + day.label + day.entries.first().commit) { HistoryDayHeader(day.label) }
                items(day.entries, key = { it.commit }) { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        busyPath = state.busyPath,
                        onFileClick = { file ->
                            when (file.target) {
                                HistoryFileTarget.NOTE -> onOpenNote(file.path)
                                HistoryFileTarget.NOTE_HISTORY -> onOpenNoteHistory(file.path)
                                HistoryFileTarget.FILE -> state.eventSink(NotesChangesEvent.OpenFile(file.path, file.name))
                                HistoryFileTarget.NONE -> Unit
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
            item(key = "status") {
                HistoryStatus(
                    isLoading = state.isLoading || state.isLoadingMore,
                    failed = state.failed,
                    emptyText = stringResource(R.string.screen_notes_history_empty).takeIf { state.days.isEmpty() },
                    onRetry = { state.eventSink(NotesChangesEvent.Retry) },
                )
            }
        }
    }
    state.fileProblem?.let { problem ->
        ErrorDialog(
            content = stringResource(
                when (problem) {
                    HistoryFileProblem.OFFLINE -> R.string.screen_notes_attachment_unavailable
                    HistoryFileProblem.NO_APP -> R.string.screen_notes_attachment_no_app
                }
            ),
            title = null,
            onSubmit = { state.eventSink(NotesChangesEvent.DismissFileProblem) },
        )
    }
}

private const val LOAD_MORE_AHEAD = 5

@PreviewsDayNight
@Composable
internal fun NotesChangesViewPreview(@PreviewParameter(NotesChangesStatePreviewParam::class) state: NotesChangesState) = ElementPreview {
    NotesChangesView(state = state, onBackClick = {}, onOpenNote = {}, onOpenNoteHistory = {})
}
