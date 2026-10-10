/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.FileChange
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Text

/** A day's header in a history list. */
@Composable
internal fun HistoryDayHeader(label: String) {
    ListSectionHeader(title = label, hasDivider = false)
}

/**
 * One change: its time and who it was for, the server's message, then the files it touched ([onFileClick] for those that can be
 * opened). With [onClick], the whole row is tappable (a note's versions).
 */
@Composable
internal fun HistoryEntryRow(
    entry: HistoryEntry,
    busyPath: String? = null,
    onFileClick: (HistoryFile) -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.time.isNotEmpty()) {
                Text(entry.time, style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
            }
            Text(
                text = entry.label.text(),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        if (entry.message.isNotBlank()) {
            Text(
                text = entry.message,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.files.forEach { file -> HistoryFileRow(file, isBusy = file.path == busyPath, onClick = { onFileClick(file) }) }
    }
}

@Composable
private fun HistoryFileRow(file: HistoryFile, isBusy: Boolean, onClick: () -> Unit) {
    val canOpen = file.target != HistoryFileTarget.NONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (isBusy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = file.change.icon(),
                    contentDescription = stringResource(file.change.description()),
                    tint = if (file.change == FileChange.DELETED) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = file.fromName?.let { stringResource(R.string.screen_notes_history_renamed, it, file.name) } ?: file.name,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = if (canOpen) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileChange.icon(): ImageVector = when (this) {
    FileChange.ADDED -> CompoundIcons.Plus()
    FileChange.MODIFIED -> CompoundIcons.Edit()
    FileChange.DELETED -> CompoundIcons.Delete()
    FileChange.RENAMED -> CompoundIcons.ArrowRight()
    FileChange.COPIED -> CompoundIcons.Copy()
}

private fun FileChange.description(): Int = when (this) {
    FileChange.ADDED -> R.string.a11y_notes_history_added
    FileChange.MODIFIED -> R.string.a11y_notes_history_modified
    FileChange.DELETED -> R.string.a11y_notes_history_deleted
    FileChange.RENAMED -> R.string.a11y_notes_history_renamed
    FileChange.COPIED -> R.string.a11y_notes_history_copied
}

/** Shown in place of a list, or at its end: loading, a failure with a retry, or a message. */
@Composable
internal fun HistoryStatus(isLoading: Boolean, failed: Boolean, emptyText: String?, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.screen_notes_history_offline),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                Button(text = stringResource(R.string.screen_notes_history_retry), onClick = onRetry)
            }
            emptyText != null -> Text(emptyText, style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textSecondary)
        }
    }
}
