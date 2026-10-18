/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** The row in Ajustes → Notificaciones that opens [NotificationHoursView]; when on, it says which days and hours. */
@Composable
fun NotificationHoursRow(settings: NotificationHoursSettings, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val summary = when {
        !settings.enabled -> null
        settings.schedule == NotificationSchedule.CUSTOM -> scheduleName(settings.schedule)
        else -> "${scheduleName(settings.schedule)} · ${clockText(context, settings.window.startMinute)}–${clockText(context, settings.window.endMinute)}"
    }
    ListItem(
        modifier = modifier,
        supportingContent = summary?.let { { Text(it) } },
        onClick = onClick,
    ) {
        Text(stringResource(R.string.screen_artise_notification_hours_title))
    }
}

@PreviewsDayNight
@Composable
internal fun NotificationHoursRowPreview() = ElementPreview {
    NotificationHoursRow(settings = NotificationHoursSettings(enabled = true, schedule = NotificationSchedule.WEEKDAYS), onClick = {})
}
