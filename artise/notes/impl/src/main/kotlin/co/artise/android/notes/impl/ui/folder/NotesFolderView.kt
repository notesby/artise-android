/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.common.NotesScaffold
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun NotesFolderView(
    state: NotesFolderState,
    onBackClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onSearchClick: () -> Unit,
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
            IconButton(onClick = onSearchClick) {
                Icon(imageVector = CompoundIcons.Search(), contentDescription = stringResource(R.string.a11y_notes_search))
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.showPrivacyNotice) {
                item {
                    Announcement(
                        title = stringResource(R.string.screen_notes_privacy_title),
                        description = stringResource(R.string.screen_notes_privacy_body),
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
                        text = stringResource(R.string.screen_notes_folder_empty),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(state.entries, key = { it.key() }) { entry ->
                when (entry) {
                    is NotesFolderEntry.Folder -> ListItem(
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Folder())),
                        trailingContent = ListItemContent.Text(pluralStringResource(R.plurals.screen_notes_folder_count, entry.noteCount, entry.noteCount)),
                        onClick = { onFolderClick(entry.path) },
                    ) {
                        Text(entry.name)
                    }
                    is NotesFolderEntry.Note -> ListItem(
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Document())),
                        supportingContent = if (entry.hasLocalEdits) {
                            { Text(stringResource(R.string.screen_notes_unsent)) }
                        } else {
                            null
                        },
                        onClick = { onNoteClick(entry.path) },
                    ) {
                        Text(entry.name)
                    }
                }
            }
        }
    }
}

private fun NotesFolderEntry.key() = when (this) {
    is NotesFolderEntry.Folder -> "folder:" + path
    is NotesFolderEntry.Note -> "note:" + path
}

@PreviewsDayNight
@Composable
internal fun NotesFolderViewPreview(@PreviewParameter(NotesFolderStatePreviewParam::class) state: NotesFolderState) = ElementPreview {
    NotesFolderView(state = state, onBackClick = {}, onFolderClick = {}, onNoteClick = {}, onSearchClick = {})
}
