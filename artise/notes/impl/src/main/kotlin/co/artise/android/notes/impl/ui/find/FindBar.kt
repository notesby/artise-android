/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.find

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The "find in this document" row: a field that searches as you type, the count ("3 of 12" or "No matches") and
 * previous/next. The keyboard's search key goes to the next match. [snippet], when set, shows under the field for a
 * match the page can't highlight itself.
 */
@Composable
internal fun FindBar(
    query: String,
    matchCount: Int,
    current: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snippet: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.screen_notes_find_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            Text(
                text = findCountText(matchCount, current),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                Icon(imageVector = CompoundIcons.ChevronUp(), contentDescription = stringResource(R.string.a11y_notes_find_previous))
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(imageVector = CompoundIcons.ChevronDown(), contentDescription = stringResource(R.string.a11y_notes_find_next))
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_close))
            }
        }
        if (snippet != null) {
            Text(
                text = snippet,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()
    }
}

/** "3 of 12", or "No matches"; nothing while the query is empty. */
@Composable
internal fun findCountText(matchCount: Int, current: Int): String = when {
    matchCount > 0 -> stringResource(R.string.screen_notes_find_count, current + 1, matchCount)
    else -> stringResource(R.string.screen_notes_find_none)
}

/** Search isn't possible for this file (too big, scanned, or a kind with no text): said where the field would be. */
@Composable
internal fun FindUnavailable(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.screen_notes_find_unavailable),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_close))
            }
        }
        HorizontalDivider()
    }
}
