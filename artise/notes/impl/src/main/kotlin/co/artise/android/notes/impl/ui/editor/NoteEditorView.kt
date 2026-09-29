/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.SaveChangesDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun NoteEditorView(
    state: NoteEditorState,
    modifier: Modifier = Modifier,
) {
    BackHandler { state.eventSink(NoteEditorEvent.Back) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = state.title,
                navigationIcon = { BackButton(onClick = { state.eventSink(NoteEditorEvent.Back) }) },
                actions = {
                    TextButton(
                        text = stringResource(CommonStrings.action_save),
                        onClick = { state.eventSink(NoteEditorEvent.Save) },
                        enabled = !state.isLoading,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
        ) {
            if (state.isResolvingConflict) {
                Text(
                    text = stringResource(R.string.screen_notes_editor_combine_hint),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            BasicTextField(
                state = state.text,
                enabled = !state.isLoading,
                textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
                cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            if (state.suggestions.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(state.suggestions, key = { it.path }) { suggestion ->
                        ListItem(
                            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Link())),
                            supportingContent = if (suggestion.linkText == suggestion.name) {
                                null
                            } else {
                                { Text(suggestion.path) }
                            },
                            onClick = { state.eventSink(NoteEditorEvent.SelectSuggestion(suggestion)) },
                        ) {
                            Text(suggestion.name)
                        }
                    }
                }
            }
        }
    }
    if (state.showSaveChangesDialog) {
        SaveChangesDialog(
            onSaveClick = { state.eventSink(NoteEditorEvent.Save) },
            onDiscardClick = { state.eventSink(NoteEditorEvent.DiscardChanges) },
            onDismiss = { state.eventSink(NoteEditorEvent.DismissSaveChangesDialog) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NoteEditorViewPreview(@PreviewParameter(NoteEditorStatePreviewParam::class) state: NoteEditorState) = ElementPreview {
    NoteEditorView(state = state)
}
