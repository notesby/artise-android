/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.UploadStatus
import co.artise.android.notes.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Under a photo or file added on this phone and not on the server yet: where its upload is, with Retry and Cancel.
 * Nothing shows once it's on the server.
 */
@Composable
internal fun UploadStatusRow(
    embed: EmbedState,
    onRetry: (path: String) -> Unit,
    onCancel: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = embed.upload ?: return
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UploadStatusIcon(status)
        Text(
            text = uploadStatusText(status),
            style = ElementTheme.typography.fontBodySmRegular,
            color = if (status == UploadStatus.FAILED) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        // While it's being sent, it can only finish: cancelling halfway could leave it on the server anyway.
        if (status != UploadStatus.UPLOADING) {
            TextButton(text = stringResource(R.string.screen_notes_upload_retry), onClick = { onRetry(embed.path) }, size = ButtonSize.Small)
            TextButton(
                text = stringResource(R.string.screen_notes_upload_cancel),
                onClick = { confirmCancel = true },
                size = ButtonSize.Small,
                destructive = true,
            )
        }
    }
    if (confirmCancel) {
        CancelUploadDialog(
            name = embed.name,
            onConfirm = {
                confirmCancel = false
                onCancel(embed.path)
            },
            onDismiss = { confirmCancel = false },
        )
    }
}

/** A small round badge for a thumbnail, showing where its upload is. */
@Composable
internal fun UploadBadge(status: UploadStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .background(ElementTheme.colors.bgCanvasDefault, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        UploadStatusIcon(status)
    }
}

/**
 * The strip's thumbnails are small: tapping one that isn't on the server yet shows its upload here, with Retry,
 * Cancel and Open.
 */
@Composable
internal fun UploadActionsDialog(
    embed: EmbedState,
    onRetry: (path: String) -> Unit,
    onCancel: (path: String) -> Unit,
    onOpen: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val status = embed.upload ?: return
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    if (confirmCancel) {
        CancelUploadDialog(
            name = embed.name,
            onConfirm = {
                onDismiss()
                onCancel(embed.path)
            },
            onDismiss = { confirmCancel = false },
        )
        return
    }
    val canAct = status != UploadStatus.UPLOADING
    ConfirmationDialog(
        title = uploadStatusText(status),
        content = embed.name,
        submitText = stringResource(if (canAct) R.string.screen_notes_upload_retry else R.string.screen_notes_upload_open),
        onSubmitClick = {
            onDismiss()
            if (canAct) onRetry(embed.path) else onOpen(embed.path)
        },
        // While it's being sent, the only other choice is to close this.
        cancelText = if (canAct) stringResource(R.string.screen_notes_upload_open) else stringResource(CommonStrings.action_close),
        onCancelClick = {
            onDismiss()
            if (canAct) onOpen(embed.path)
        },
        thirdButtonText = if (canAct) stringResource(R.string.screen_notes_upload_cancel) else null,
        onThirdButtonClick = { confirmCancel = true },
        onDismiss = onDismiss,
    )
}

@Composable
private fun CancelUploadDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.screen_notes_upload_cancel_title),
        content = stringResource(R.string.screen_notes_upload_cancel_message, name),
        submitText = stringResource(R.string.screen_notes_upload_cancel),
        cancelText = stringResource(R.string.screen_notes_upload_keep),
        destructiveSubmit = true,
        onSubmitClick = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun UploadStatusIcon(status: UploadStatus) {
    when (status) {
        UploadStatus.UPLOADING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        UploadStatus.WAITING -> Icon(CompoundIcons.Time(), contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(16.dp))
        UploadStatus.RETRYING -> Icon(
            CompoundIcons.Restart(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(16.dp)
        )
        UploadStatus.FAILED -> Icon(
            CompoundIcons.ErrorSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconCriticalPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun uploadStatusText(status: UploadStatus): String = stringResource(
    when (status) {
        UploadStatus.WAITING -> R.string.screen_notes_upload_waiting
        UploadStatus.UPLOADING -> R.string.screen_notes_upload_uploading
        UploadStatus.RETRYING -> R.string.screen_notes_upload_retrying
        UploadStatus.FAILED -> R.string.screen_notes_upload_failed
    }
)
