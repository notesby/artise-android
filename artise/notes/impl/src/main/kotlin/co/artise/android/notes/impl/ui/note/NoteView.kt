/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

@Composable
fun NoteView(
    state: NoteState,
    onBackClick: () -> Unit,
    onBacklinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(titleStr = state.title, navigationIcon = { BackButton(onClick = onBackClick) }) },
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
    state.missingNote?.let { name ->
        ErrorDialog(
            content = stringResource(R.string.screen_notes_missing_note, name),
            title = null,
            onSubmit = { state.eventSink(NoteEvent.DismissMissingNote) },
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
    NoteView(state = state, onBackClick = {}, onBacklinkClick = {})
}
