/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.note.openDownloadedFile
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.toImmutableList

/** What can be done with a note or file (a sheet after a long press), and the dialogs that follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderActionsAndDialogs(state: NotesFolderState) {
    val context = LocalContext.current
    state.openFile?.let { request ->
        LaunchedEffect(request) { state.eventSink(NotesFolderEvent.FileOpenHandled(opened = context.openDownloadedFile(request))) }
    }
    state.actionsFor?.let { entry ->
        ModalBottomSheet(onDismissRequest = { state.eventSink(NotesFolderEvent.DismissActions) }, scrollable = false) {
            Column(Modifier.navigationBarsPadding()) {
                Text(
                    text = entry.name,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (entry is NotesFolderEntry.File) {
                    ActionItem(stringResource(R.string.screen_notes_file_open), IconSource.Vector(CompoundIcons.PopOut())) {
                        state.eventSink(NotesFolderEvent.OpenFile(entry))
                    }
                }
                ActionItem(stringResource(R.string.screen_notes_rename), IconSource.Vector(CompoundIcons.Edit())) {
                    state.eventSink(NotesFolderEvent.StartRename)
                }
                ActionItem(stringResource(R.string.screen_notes_file_move), IconSource.Vector(CompoundIcons.Folder())) {
                    state.eventSink(NotesFolderEvent.StartMove)
                }
                ActionItem(stringResource(R.string.screen_notes_delete), IconSource.Vector(CompoundIcons.Delete()), destructive = true) {
                    state.eventSink(NotesFolderEvent.StartDelete)
                }
            }
        }
    }
    val dismiss = { state.eventSink(NotesFolderEvent.DismissDialog) }
    when (val dialog = state.dialog) {
        null -> Unit
        is FolderDialog.Rename -> TextFieldDialog(
            title = stringResource(R.string.screen_notes_rename),
            value = editableName(dialog.entry),
            placeholder = null,
            onSubmit = { state.eventSink(NotesFolderEvent.Rename(it)) },
            onDismissRequest = dismiss,
            validation = { !it.isNullOrBlank() },
            supportingText = dialog.problem?.let { nameProblemText(it) },
            submitText = stringResource(R.string.screen_notes_rename),
        )
        is FolderDialog.MoveTo -> {
            val topLevel = stringResource(R.string.screen_notes_file_move_top_level)
            val newFolder = stringResource(R.string.screen_notes_file_move_new_folder)
            val here = stringResource(R.string.screen_notes_file_move_here)
            val targets = listOf("") + dialog.folders
            val options = targets.map { folder ->
                ListOption(
                    title = if (folder.isEmpty()) topLevel else folder.replace("/", " / "),
                    subtitle = if (folder == dialog.current) here else null,
                )
            } + ListOption(newFolder)
            SingleSelectionDialog(
                title = stringResource(R.string.screen_notes_file_move_title, dialog.entry.name),
                options = options.toImmutableList(),
                initialSelection = targets.indexOf(dialog.current).takeIf { it >= 0 },
                onSelectOption = { index ->
                    if (index == targets.size) {
                        state.eventSink(NotesFolderEvent.StartNewFolder)
                    } else {
                        state.eventSink(NotesFolderEvent.MoveTo(targets[index]))
                    }
                },
                onDismissRequest = dismiss,
            )
        }
        is FolderDialog.NewFolder -> TextFieldDialog(
            title = stringResource(R.string.screen_notes_file_move_new_folder),
            value = null,
            placeholder = stringResource(R.string.screen_notes_folder_name_placeholder),
            onSubmit = { state.eventSink(NotesFolderEvent.MoveToNewFolder(it)) },
            onDismissRequest = dismiss,
            validation = { !it.isNullOrBlank() },
            supportingText = dialog.problem?.let { nameProblemText(it) } ?: stringResource(R.string.screen_notes_folder_name_hint),
            submitText = stringResource(R.string.screen_notes_file_move),
        )
        is FolderDialog.ConfirmDelete -> ConfirmationDialog(
            title = stringResource(R.string.screen_notes_file_delete_title, dialog.entry.name),
            content = stringResource(
                if (dialog.entry is NotesFolderEntry.File) R.string.screen_notes_file_delete_file_message else R.string.screen_notes_file_delete_note_message
            ),
            submitText = stringResource(R.string.screen_notes_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NotesFolderEvent.ConfirmDelete) },
            onDismiss = dismiss,
        )
        is FolderDialog.FileInUse -> ConfirmationDialog(
            title = stringResource(R.string.screen_notes_media_in_use_title, dialog.entry.name),
            content = stringResource(
                R.string.screen_notes_media_in_use_message,
                dialog.usedBy.joinToString("\n") { "• " + NotesFolderEntries.noteName(it) },
            ),
            submitText = stringResource(R.string.screen_notes_media_remove_and_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(NotesFolderEvent.ConfirmRemoveAndDelete) },
            onDismiss = dismiss,
        )
        is FolderDialog.Problem -> ErrorDialog(
            content = stringResource(
                when (dialog.problem) {
                    FileProblem.OFFLINE -> R.string.screen_notes_file_problem_offline
                    FileProblem.UNSENT -> R.string.screen_notes_file_problem_unsent
                    FileProblem.EXISTS -> R.string.screen_notes_file_problem_exists
                    FileProblem.NO_APP -> R.string.screen_notes_attachment_no_app
                    FileProblem.FAILED -> R.string.screen_notes_file_problem_failed
                }
            ),
            onSubmit = dismiss,
        )
    }
}

@Composable
private fun ActionItem(text: String, icon: IconSource, destructive: Boolean = false, onClick: () -> Unit) {
    ListItem(
        leadingContent = ListItemContent.Icon(icon),
        style = if (destructive) ListItemStyle.Destructive else ListItemStyle.Default,
        onClick = onClick,
    ) {
        Text(text)
    }
}

/** The name as people edit it: a note without ".md", a file without its extension (kept when renaming). */
private fun editableName(entry: NotesFolderEntry): String = when (entry) {
    is NotesFolderEntry.File -> entry.name.substringBeforeLast('.', entry.name)
    else -> entry.name
}
