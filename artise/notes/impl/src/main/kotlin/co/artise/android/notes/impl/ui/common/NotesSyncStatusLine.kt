/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text

/** One calm line saying the list may be out of date; nothing when all is well. */
@Composable
internal fun NotesSyncStatusLine(status: NotesSyncStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        NotesSyncStatus.OK -> return
        NotesSyncStatus.OFFLINE -> stringResource(R.string.screen_notes_offline)
        NotesSyncStatus.FAILED -> stringResource(R.string.screen_notes_sync_failed)
    }
    Text(
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
