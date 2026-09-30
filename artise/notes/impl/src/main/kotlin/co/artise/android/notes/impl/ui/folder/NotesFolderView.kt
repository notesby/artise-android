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
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NotesScaffold
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
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
    onReviewChoicesClick: () -> Unit,
    onMapClick: () -> Unit,
    onMediaClick: () -> Unit,
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
            IconButton(onClick = onSearchClick) {
                Icon(imageVector = CompoundIcons.Search(), contentDescription = stringResource(R.string.a11y_notes_search))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { state.eventSink(NotesFolderEvent.StartNewNote) }) {
                Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_notes_new_note))
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
}

@Composable
internal fun nameProblemText(problem: NoteNameProblem): String = when (problem) {
    NoteNameProblem.INVALID -> stringResource(R.string.screen_notes_name_invalid)
    NoteNameProblem.EXISTS -> stringResource(R.string.screen_notes_name_exists)
}

private fun NotesFolderEntry.key() = when (this) {
    is NotesFolderEntry.Folder -> "folder:" + path
    is NotesFolderEntry.Note -> "note:" + path
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
        onMediaClick = {}
    )
}
