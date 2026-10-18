/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.media

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.note.EmbedState
import co.artise.android.notes.impl.ui.note.UploadStatusRow
import co.artise.android.notes.impl.ui.note.openDownloadedFile
import co.artise.android.notes.impl.ui.note.photoRequest
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/** A chat's photos and files: what each note uses, what nothing uses, and deleting them carefully. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesMediaView(
    state: NotesMediaState,
    onBackClick: () -> Unit,
    onNoteClick: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    state.openFile?.let { request ->
        LaunchedEffect(request) { state.eventSink(NotesMediaEvent.FileOpenHandled(opened = context.openDownloadedFile(request))) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_notes_media_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    index = 0,
                    count = 2,
                    selected = state.filter == MediaFilter.ALL,
                    onClick = { state.eventSink(NotesMediaEvent.SetFilter(MediaFilter.ALL)) },
                    text = stringResource(R.string.screen_notes_media_all),
                )
                SegmentedButton(
                    index = 1,
                    count = 2,
                    selected = state.filter == MediaFilter.UNUSED,
                    onClick = { state.eventSink(NotesMediaEvent.SetFilter(MediaFilter.UNUSED)) },
                    text = stringResource(R.string.screen_notes_media_unused, state.unusedCount),
                )
            }
            if (state.filter == MediaFilter.UNUSED && state.unusedCount > 0) {
                OutlinedButton(
                    text = stringResource(R.string.screen_notes_media_delete_unused, Formatter.formatShortFileSize(context, state.unusedBytes)),
                    onClick = { state.eventSink(NotesMediaEvent.DeleteUnused) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.items.isEmpty() -> Text(
                    text = stringResource(if (state.filter ==
                        MediaFilter.UNUSED) {
                            R.string.screen_notes_media_none_unused
                        } else {
                            R.string.screen_notes_media_empty
                        }),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.path }) { item ->
                        MediaRow(
                            item = item,
                            isDeleting = state.deleting == item.path,
                            onClick = { state.eventSink(NotesMediaEvent.Open(item)) },
                            onDelete = { state.eventSink(NotesMediaEvent.Delete(item)) },
                            onRetry = { state.eventSink(NotesMediaEvent.RetryUpload(item)) },
                            onNoteClick = onNoteClick,
                        )
                    }
                }
            }
        }
    }
    MediaDialogs(state)
}

@Composable
private fun MediaRow(
    item: MediaItem,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onNoteClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(ElementTheme.colors.bgSubtleSecondary, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isImage && item.file != null) {
                    AsyncImage(
                        model = photoRequest(item.file),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(imageVector = if (item.isImage) CompoundIcons.Image() else CompoundIcons.Document(), contentDescription = null)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(item.name, style = ElementTheme.typography.fontBodyLgMedium, maxLines = 1)
                Text(
                    text = listOf(
                        Formatter.formatShortFileSize(context, item.size),
                        if (item.usedBy.isEmpty()) {
                            stringResource(R.string.screen_notes_media_not_used)
                        } else {
                            pluralStringResource(R.plurals.screen_notes_media_used_in, item.usedBy.size, item.usedBy.size)
                        },
                    ).joinToString(" · "),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = if (item.usedBy.isEmpty()) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                )
            }
            IconButton(onClick = onClick) {
                Icon(imageVector = CompoundIcons.PopOut(), contentDescription = stringResource(R.string.screen_notes_media_open))
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDelete, enabled = item.upload == null || item.usedBy.isEmpty()) {
                    Icon(
                        imageVector = CompoundIcons.Delete(),
                        contentDescription = stringResource(R.string.screen_notes_media_delete),
                        tint = ElementTheme.colors.iconCriticalPrimary
                    )
                }
            }
        }
        // Which notes use it, each opening that note.
        item.usedBy.forEach { note ->
            Text(
                text = NotesFolderEntries.noteName(note),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textLinkExternal,
                modifier = Modifier
                    .padding(start = 68.dp, top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNoteClick(note) }
                    .padding(2.dp),
            )
        }
        UploadStatusRow(
            embed = EmbedState(
                path = item.path,
                name = item.name,
                isImage = item.isImage,
                file = item.file,
                failed = false,
                size = item.size,
                isDownloading = false,
                upload = item.upload,
            ),
            onRetry = { onRetry() },
            onCancel = { onDelete() },
            modifier = Modifier.padding(start = 68.dp),
        )
    }
}

@Composable
private fun MediaDialogs(state: NotesMediaState) {
    val context = LocalContext.current
    val dismiss = { state.eventSink(NotesMediaEvent.DismissDialog) }
    when (val dialog = state.dialog) {
        null -> Unit
        is MediaDialog.ConfirmDelete -> ConfirmationDialog(
            title = stringResource(R.string.screen_notes_media_delete_title, dialog.item.name),
            content = stringResource(R.string.screen_notes_media_delete_message),
            submitText = stringResource(R.string.screen_notes_media_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NotesMediaEvent.ConfirmDelete) },
            onDismiss = dismiss,
        )
        is MediaDialog.ConfirmRemoveAndDelete -> ConfirmationDialog(
            title = stringResource(R.string.screen_notes_media_in_use_title, dialog.item.name),
            content = stringResource(
                R.string.screen_notes_media_in_use_message,
                dialog.usedBy.joinToString("\n") { "• " + NotesFolderEntries.noteName(it) },
            ),
            submitText = stringResource(R.string.screen_notes_media_remove_and_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NotesMediaEvent.ConfirmDelete) },
            onDismiss = dismiss,
        )
        is MediaDialog.ConfirmDeleteUnused -> ConfirmationDialog(
            title = pluralStringResource(R.plurals.screen_notes_media_delete_unused_title, dialog.count, dialog.count),
            content = stringResource(R.string.screen_notes_media_delete_unused_message, Formatter.formatShortFileSize(context, dialog.bytes)),
            submitText = stringResource(R.string.screen_notes_media_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NotesMediaEvent.ConfirmDeleteUnused) },
            onDismiss = dismiss,
        )
        MediaDialog.Offline -> ErrorDialog(content = stringResource(R.string.screen_notes_media_offline), onSubmit = dismiss)
        MediaDialog.Failed -> ErrorDialog(content = stringResource(R.string.screen_notes_media_failed), onSubmit = dismiss)
        MediaDialog.NoAppForFile -> ErrorDialog(content = stringResource(R.string.screen_notes_attachment_no_app), onSubmit = dismiss)
    }
}

@PreviewsDayNight
@Composable
internal fun NotesMediaViewPreview(@PreviewParameter(NotesMediaStatePreviewParam::class) state: NotesMediaState) = ElementPreview {
    NotesMediaView(state = state, onBackClick = {}, onNoteClick = {})
}
