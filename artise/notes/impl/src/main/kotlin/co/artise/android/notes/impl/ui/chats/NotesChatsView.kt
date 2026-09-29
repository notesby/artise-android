/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.common.NotesScaffold
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId

@Composable
fun NotesChatsView(
    state: NotesChatsState,
    onBackClick: () -> Unit,
    onChatClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    NotesScaffold(
        title = stringResource(R.string.screen_notes_title),
        onBackClick = onBackClick,
        sync = state.sync,
        isRefreshing = state.isRefreshing,
        onRefresh = { state.eventSink(NotesChatsEvent.Refresh) },
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.chats.isEmpty() && !state.isRefreshing) {
                item {
                    Text(
                        text = stringResource(R.string.screen_notes_chats_empty),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(state.chats, key = { it.roomId.value }) { chat ->
                ListItem(
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Chat())),
                    onClick = { onChatClick(chat.roomId) },
                ) {
                    Text(chat.name)
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun NotesChatsViewPreview(@PreviewParameter(NotesChatsStatePreviewParam::class) state: NotesChatsState) = ElementPreview {
    NotesChatsView(state = state, onBackClick = {}, onChatClick = {})
}
