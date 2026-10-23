/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.card.iconOf
import co.artise.android.notes.impl.ui.card.isDrawn
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NotesScaffold
import co.artise.android.notes.impl.ui.note.photoRequest
import co.artise.android.workspace.api.LocalWorkspaceBranding
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun NotesFolderView(
    state: NotesFolderState,
    onBackClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onReviewChoicesClick: () -> Unit,
    onMapClick: () -> Unit,
    onMediaClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onChartClick: (String) -> Unit,
    onDashboardClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NotesScaffold(
        title = state.title.ifEmpty { stringResource(R.string.screen_notes_title) },
        onBackClick = onBackClick,
        sync = state.sync,
        isRefreshing = state.isRefreshing,
        onRefresh = { state.eventSink(NotesFolderEvent.Refresh) },
        modifier = modifier,
        actions = {
            IconButton(onClick = onMediaClick) {
                Icon(imageVector = CompoundIcons.Image(), contentDescription = stringResource(R.string.screen_notes_media_title))
            }
            IconButton(onClick = onMapClick) {
                Icon(imageVector = CompoundIcons.Explore(), contentDescription = stringResource(R.string.screen_notes_map))
            }
            IconButton(onClick = onHistoryClick) {
                Icon(imageVector = CompoundIcons.History(), contentDescription = stringResource(R.string.screen_notes_history_title))
            }
            IconButton(onClick = onSearchClick) {
                Icon(imageVector = CompoundIcons.Search(), contentDescription = stringResource(R.string.a11y_notes_search))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { state.eventSink(NotesFolderEvent.ShowNewMenu) }) {
                Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_notes_new))
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.needChoiceCount > 0) {
                item {
                    Announcement(
                        title = pluralStringResource(R.plurals.screen_notes_choices_banner, state.needChoiceCount, state.needChoiceCount),
                        description = null,
                        type = AnnouncementType.Actionable(
                            actionText = stringResource(R.string.screen_notes_choices_review),
                            onActionClick = onReviewChoicesClick,
                            onDismissClick = null,
                        ),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.showPrivacyNotice) {
                item {
                    Announcement(
                        title = stringResource(R.string.screen_notes_privacy_title),
                        description = stringResource(R.string.screen_notes_privacy_body, LocalWorkspaceBranding.current.assistantName),
                        type = AnnouncementType.Actionable(
                            actionText = stringResource(R.string.screen_notes_privacy_ok),
                            onActionClick = { state.eventSink(NotesFolderEvent.DismissPrivacyNotice) },
                            onDismissClick = { state.eventSink(NotesFolderEvent.DismissPrivacyNotice) },
                        ),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.entries.isEmpty() && !state.isRefreshing) {
                item {
                    Text(
                        text = stringResource(if (state.canDeleteFolder) R.string.screen_notes_folder_is_empty else R.string.screen_notes_folder_empty),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (state.canDeleteFolder) {
                item {
                    // A folder emptied by moving everything out stays on the server until someone deletes it.
                    OutlinedButton(
                        text = stringResource(R.string.screen_notes_folder_delete_this),
                        onClick = { state.eventSink(NotesFolderEvent.DeleteThisFolder) },
                        showProgress = state.busyPath != null,
                        destructive = true,
                        leadingIcon = IconSource.Vector(CompoundIcons.Delete()),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            items(state.entries, key = { it.key() }) { entry ->
                val isBusy = state.busyPath == entry.path
                val actionsLabel = stringResource(R.string.screen_notes_file_actions)

                // A tap opens; a long press shows rename, move and delete.
                fun Modifier.clicks(onClick: () -> Unit) = combinedClickable(
                    onClick = onClick,
                    onLongClick = { state.eventSink(NotesFolderEvent.ShowActions(entry)) },
                    onLongClickLabel = actionsLabel,
                )
                when (entry) {
                    is NotesFolderEntry.Folder -> ListItem(
                        modifier = Modifier.clicks { onFolderClick(entry.path) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Folder())),
                        trailingContent = if (isBusy) {
                            ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else {
                            ListItemContent.Text(folderCount(entry))
                        },
                    ) {
                        Text(entry.name)
                    }
                    is NotesFolderEntry.Note -> if (entry.kind.isDrawn) {
                        DrawnRow(
                            entry = entry,
                            preview = state.previews[entry.path],
                            isBusy = isBusy,
                            onLoadPreview = { dark -> state.eventSink(NotesFolderEvent.LoadPreview(entry.path, dark)) },
                            modifier = Modifier.clicks {
                                if (entry.kind == NoteAttachment.Kind.CHART) onChartClick(entry.path) else onDashboardClick(entry.path)
                            },
                        )
                    } else {
                        ListItem(
                        modifier = Modifier.clicks { onNoteClick(entry.path) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Document())),
                        trailingContent = if (isBusy) ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } else null,
                        supportingContent = when {
                            entry.hasLocalEdits -> {
                                { Text(stringResource(R.string.screen_notes_unsent)) }
                            }
                            entry.firstLines.isNotEmpty() -> {
                                { Text(entry.firstLines, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                            else -> null
                        },
                    ) {
                        Text(entry.name)
                    }
                    }
                    is NotesFolderEntry.File -> {
                        val preview = state.previews[entry.path]
                        // The row is on screen: ask what the file looks like (again when the file changed).
                        LaunchedEffect(entry) { state.eventSink(NotesFolderEvent.LoadPreview(entry.path)) }
                        val kind = NoteAttachment.kindOf(entry.path)
                        val opensInApp = kind != NoteAttachment.Kind.NOTE && kind != NoteAttachment.Kind.OTHER
                        ListItem(
                            modifier = Modifier.clicks {
                                if (opensInApp) onDocumentClick(entry.path) else state.eventSink(NotesFolderEvent.OpenFile(entry))
                            },
                            leadingContent = if (preview is NotesPreview.Image) {
                                ListItemContent.Custom { FilePreviewThumbnail(preview, entry.name) }
                            } else {
                                ListItemContent.Icon(IconSource.Vector(if (entry.isImage) CompoundIcons.Image() else CompoundIcons.Attachment()))
                            },
                            trailingContent = if (isBusy) {
                                ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                            supportingContent = {
                                if (preview is NotesPreview.Text && preview.text.isNotBlank()) {
                                    Text(preview.text.trim(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text(Formatter.formatShortFileSize(LocalContext.current, entry.size))
                                }
                            },
                        ) {
                            Text(entry.name)
                        }
                    }
                }
            }
        }
    }
    state.newNote?.let { dialog ->
        TextFieldDialog(
            title = stringResource(R.string.screen_notes_new_note),
            value = null,
            placeholder = stringResource(R.string.screen_notes_new_note_placeholder),
            onSubmit = { state.eventSink(NotesFolderEvent.CreateNote(it)) },
            onDismissRequest = { state.eventSink(NotesFolderEvent.CancelNewNote) },
            // Blank names can't be submitted; other problems come back from the attempt and show under the field.
            validation = { !it.isNullOrBlank() },
            supportingText = dialog.problem?.let { problem -> nameProblemText(problem) },
            submitText = stringResource(R.string.screen_notes_create),
        )
    }
    FolderActionsAndDialogs(state)
    if (state.isGone) {
        val leave by rememberUpdatedState(onBackClick)
        LaunchedEffect(Unit) { leave() }
    }
}

@Composable
internal fun nameProblemText(problem: NoteNameProblem): String = when (problem) {
    NoteNameProblem.INVALID -> stringResource(R.string.screen_notes_name_invalid)
    NoteNameProblem.EXISTS -> stringResource(R.string.screen_notes_name_exists)
}

@Composable
private fun folderCount(entry: NotesFolderEntry.Folder): String {
    val notes = pluralStringResource(R.plurals.screen_notes_folder_count, entry.noteCount, entry.noteCount)
    return if (entry.fileCount == 0) notes else notes + " · " + pluralStringResource(R.plurals.screen_notes_folder_file_count, entry.fileCount, entry.fileCount)
}

private fun NotesFolderEntry.key() = when (this) {
    is NotesFolderEntry.Folder -> "folder:" + path
    is NotesFolderEntry.Note -> "note:" + path
    is NotesFolderEntry.File -> "file:" + path
}

@PreviewsDayNight
@Composable
internal fun NotesFolderViewPreview(@PreviewParameter(NotesFolderStatePreviewParam::class) state: NotesFolderState) = ElementPreview {
    NotesFolderView(
        state = state,
        onBackClick = {},
        onFolderClick = {},
        onNoteClick = {},
        onSearchClick = {},
        onReviewChoicesClick = {},
        onMapClick = {},
        onMediaClick = {},
        onDocumentClick = {},
        onChartClick = {},
        onDashboardClick = {},
        onHistoryClick = {},
    )
}

/** A chart or dashboard: its picture (drawn for this screen's light or dark), its title, and its kind. */
@Composable
private fun DrawnRow(
    entry: NotesFolderEntry.Note,
    preview: NotesPreview?,
    isBusy: Boolean,
    onLoadPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = !ElementTheme.isLightTheme
    val loadPreview by rememberUpdatedState(onLoadPreview)
    LaunchedEffect(entry, dark) { loadPreview(dark) }
    ListItem(
        modifier = modifier,
        leadingContent = if (preview is NotesPreview.Image) {
            ListItemContent.Custom { FilePreviewThumbnail(preview, entry.name, isChart = entry.kind == NoteAttachment.Kind.CHART) }
        } else {
            ListItemContent.Icon(IconSource.Vector(iconOf(entry.kind)))
        },
        trailingContent = if (isBusy) ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } else null,
        supportingContent = {
            Text(
                if (entry.hasLocalEdits) {
                    stringResource(R.string.screen_notes_unsent)
                } else {
                    stringResource(if (entry.kind == NoteAttachment.Kind.CHART) R.string.screen_notes_kind_chart else R.string.screen_notes_kind_dashboard)
                },
            )
        },
    ) {
        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A document's first page or a photo, small, where the row's icon would be. */
@Composable
private fun FilePreviewThumbnail(preview: NotesPreview.Image, name: String, isChart: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            // A chart is wider than tall: shown whole.
            .size(width = if (isChart) 72.dp else 44.dp, height = if (isChart) 44.dp else 56.dp)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondary, shape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photoRequest(preview.file.path),
            contentDescription = name,
            contentScale = if (isChart) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
