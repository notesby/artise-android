/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import co.artise.android.recovery.api.R as ArtiseRecoveryR

/** Artise: after "Hacerlo después" on identity confirmation, earlier encrypted messages stay locked until it's done; no dismiss. */
@Composable
internal fun UnlockMessagesBanner(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = stringResource(ArtiseRecoveryR.string.artise_recovery_locked_title),
        description = stringResource(ArtiseRecoveryR.string.artise_recovery_locked_message),
        type = AnnouncementType.Actionable(
            actionText = stringResource(ArtiseRecoveryR.string.artise_recovery_locked_action),
            onActionClick = onUnlockClick,
            onDismissClick = null,
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun UnlockMessagesBannerPreview() = ElementPreview {
    UnlockMessagesBanner(onUnlockClick = {})
}
