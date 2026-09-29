/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.markdown.NoteLink
import co.artise.android.notes.impl.markdown.NoteMarkdownView
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.folder.nameProblemText
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun NoteView(
    state: NoteState,
    onBackClick: () -> Unit,
    onBacklinkClick: (String) -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = state.title,
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = { NoteActions(state, onEditClick) },
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
            if (state.hasLocalEdits) {
                Text(
                    text = stringResource(R.string.screen_notes_unsent),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                state.content != null -> NoteMarkdownView(
                    markdown = state.content,
                    onLinkClick = { link ->
                        when (link) {
                            is NoteLink.Note -> state.eventSink(NoteEvent.OpenNoteLink(link.target))
                            is NoteLink.Web -> uriHandler.openUri(link.url)
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                    onTaskToggle = { line -> state.eventSink(NoteEvent.ToggleTask(line)) },
                )
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Text(
                    text = stringResource(R.string.screen_notes_not_downloaded),
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Backlinks(state.backlinks, onBacklinkClick)
        }
    }
    NoteDialogs(state)
}

@Composable
private fun RowScope.NoteActions(state: NoteState, onEditClick: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = onEditClick, enabled = state.canEdit) {
        Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.screen_notes_edit))
    }
    IconButton(onClick = { showMenu = true }) {
        Icon(imageVector = CompoundIcons.OverflowVertical(), contentDescription = stringResource(R.string.screen_notes_more))
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.screen_notes_rename)) },
            onClick = {
                showMenu = false
                state.eventSink(NoteEvent.StartRename)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.screen_notes_delete)) },
            onClick = {
                showMenu = false
                state.eventSink(NoteEvent.StartDelete)
            },
        )
    }
}

@Composable
private fun NoteDialogs(state: NoteState) {
    val dismiss = { state.eventSink(NoteEvent.DismissDialog) }
    when (val dialog = state.dialog) {
        null -> Unit
        is NoteDialog.MissingNote -> ConfirmationDialog(
            content = stringResource(R.string.screen_notes_missing_note_create, dialog.target),
            submitText = stringResource(R.string.screen_notes_create),
            onSubmitClick = { state.eventSink(NoteEvent.CreateMissingNote) },
            onDismiss = dismiss,
        )
        is NoteDialog.Rename -> TextFieldDialog(
            title = stringResource(R.string.screen_notes_rename),
            value = dialog.currentName,
            placeholder = stringResource(R.string.screen_notes_new_note_placeholder),
            onSubmit = { state.eventSink(NoteEvent.Rename(it)) },
            onDismissRequest = dismiss,
            validation = { !it.isNullOrBlank() },
            supportingText = dialog.problem?.let { nameProblemText(it) },
            submitText = stringResource(CommonStrings.action_save),
        )
        NoteDialog.ConfirmDelete -> ConfirmationDialog(
            title = stringResource(R.string.screen_notes_delete_title, state.title),
            content = stringResource(R.string.screen_notes_delete_body),
            submitText = stringResource(R.string.screen_notes_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NoteEvent.ConfirmDelete) },
            onDismiss = dismiss,
        )
        is NoteDialog.RenameFailed -> ErrorDialog(
            content = when (dialog.reason) {
                RenameFailure.OFFLINE -> stringResource(R.string.screen_notes_rename_offline)
                RenameFailure.UNSENT_CHANGES -> stringResource(R.string.screen_notes_rename_unsent)
                RenameFailure.OTHER -> stringResource(R.string.screen_notes_rename_failed)
            },
            title = null,
            onSubmit = dismiss,
        )
    }
}

@Composable
private fun Backlinks(state: BacklinksState, onBacklinkClick: (String) -> Unit) = Column {
    ListSectionHeader(title = stringResource(R.string.screen_notes_linked_from), hasDivider = false)
    val message = when (state) {
        BacklinksState.Loading -> null
        BacklinksState.Offline -> stringResource(R.string.screen_notes_linked_from_offline)
        is BacklinksState.Loaded -> if (state.backlinks.isEmpty()) stringResource(R.string.screen_notes_linked_from_none) else null
    }
    if (message != null) {
        Text(
            text = message,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (state is BacklinksState.Loaded) {
        state.backlinks.forEach { backlink ->
            ListItem(
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Link())),
                supportingContent = { Text(backlink.line.trim(), maxLines = 2) },
                onClick = { onBacklinkClick(backlink.path) },
            ) {
                Text(NotesFolderEntries.noteName(backlink.path))
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NoteViewPreview(@PreviewParameter(NoteStatePreviewParam::class) state: NoteState) = ElementPreview {
    NoteView(state = state, onBackClick = {}, onBacklinkClick = {}, onEditClick = {})
}
