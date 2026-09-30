/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

@Composable
fun NotesSearchView(
    state: NotesSearchState,
    onBackClick: () -> Unit,
    onResultClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        state = state.query,
                        placeholder = stringResource(R.string.screen_notes_search_placeholder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .focusRequester(focusRequester),
                    )
                },
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
            val query = state.query.text.toString().trim()
            if (query.isNotEmpty() && state.results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.screen_notes_search_empty, query),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(state.results, key = { it.path }) { hit ->
                ListItem(
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Document())),
                    supportingContent = if (hit.lines.isEmpty()) {
                        null
                    } else {
                        { Text(hit.lines.joinToString("\n"), maxLines = 3) }
                    },
                    onClick = { onResultClick(hit.path) },
                ) {
                    Text(NotesFolderEntries.noteName(hit.path))
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NotesSearchViewPreview(@PreviewParameter(NotesSearchStatePreviewParam::class) state: NotesSearchState) = ElementPreview {
    NotesSearchView(state = state, onBackClick = {}, onResultClick = {})
}
