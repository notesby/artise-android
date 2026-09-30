/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.choices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

@Composable
fun NotesChoicesView(
    state: NotesChoicesState,
    onBackClick: () -> Unit,
    onCombineClick: (NotesChoiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_notes_choices_title),
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
            if (state.items.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.screen_notes_choices_empty),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(state.items, key = { it.editId }) { item ->
                ChoiceCard(item, state.eventSink, onCombineClick)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChoiceCard(item: NotesChoiceItem, eventSink: (NotesChoicesEvent) -> Unit, onCombineClick: (NotesChoiceItem) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(item.name, style = ElementTheme.typography.fontHeadingSmMedium, color = ElementTheme.colors.textPrimary)
        Text(explanation(item), style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
        if (item.state == EditState.CONFLICT && item.kind == EditKind.SAVE || item.state == EditState.EXISTS) {
            Version(stringResource(R.string.screen_notes_choice_mine), item.mine)
            Version(stringResource(R.string.screen_notes_choice_theirs), item.theirs)
        }
        when {
            item.state == EditState.CONFLICT && item.kind == EditKind.SAVE -> {
                Choice(stringResource(R.string.screen_notes_choice_keep_mine), primary = true) { eventSink(NotesChoicesEvent.KeepMine(item.editId)) }
                Choice(stringResource(R.string.screen_notes_choice_combine)) { onCombineClick(item) }
                Choice(stringResource(R.string.screen_notes_choice_keep_theirs)) { eventSink(NotesChoicesEvent.Discard(item.editId)) }
            }
            item.state == EditState.DELETED -> {
                Choice(stringResource(R.string.screen_notes_choice_keep_copy), primary = true) { eventSink(NotesChoicesEvent.KeepMyCopy(item.editId)) }
                Choice(stringResource(R.string.screen_notes_choice_discard)) { eventSink(NotesChoicesEvent.Discard(item.editId)) }
            }
            item.state == EditState.EXISTS -> {
                Choice(stringResource(R.string.screen_notes_choice_keep_both), primary = true) { eventSink(NotesChoicesEvent.KeepBoth(item.editId)) }
                Choice(stringResource(R.string.screen_notes_choice_discard)) { eventSink(NotesChoicesEvent.Discard(item.editId)) }
            }
            // A photo or file the server refused: drop it.
            item.kind == EditKind.UPLOAD -> Choice(stringResource(R.string.screen_notes_choice_discard), primary = true) {
                eventSink(NotesChoicesEvent.Discard(item.editId))
            }
            // A deletion someone overtook with an edit, or a refused change: the server's copy stays.
            else -> Choice(stringResource(R.string.screen_notes_choice_keep_theirs), primary = true) { eventSink(NotesChoicesEvent.Discard(item.editId)) }
        }
    }
}

@Composable
private fun explanation(item: NotesChoiceItem): String = when (item.state) {
    EditState.CONFLICT -> if (item.kind == EditKind.DELETE) {
        stringResource(R.string.screen_notes_choice_delete_conflict)
    } else {
        stringResource(R.string.screen_notes_choice_conflict)
    }
    EditState.DELETED -> stringResource(R.string.screen_notes_choice_deleted)
    EditState.EXISTS -> stringResource(R.string.screen_notes_choice_exists)
    EditState.REJECTED, EditState.PENDING -> stringResource(R.string.screen_notes_choice_rejected, item.error.orEmpty())
}

@Composable
private fun Version(label: String, text: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
        Text(
            text = text.orEmpty(),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
    }
}

@Composable
private fun Choice(text: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedButton(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}

@PreviewsDayNight
@Composable
internal fun NotesChoicesViewPreview(@PreviewParameter(NotesChoicesStatePreviewParam::class) state: NotesChoicesState) = ElementPreview {
    NotesChoicesView(state = state, onBackClick = {}, onCombineClick = {})
}
