/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.artise

import android.media.AudioAttributes
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import io.element.android.libraries.push.impl.R
import io.element.android.services.toolbox.api.strings.StringProvider

/** The "Reminders" channel: Ari's important and critical automation posts, kept apart so muting a busy chat doesn't hide them. */
internal const val REMINDER_NOTIFICATION_CHANNEL_ID = "ARTISE_REMINDER_NOTIFICATION_CHANNEL_ID"

/**
 * High importance (heads-up), with the system's notification sound rather than the chosen message sound, so silencing
 * messages doesn't silence reminders. People can let it through Do Not Disturb from the channel's settings.
 */
internal fun buildReminderChannel(stringProvider: StringProvider, @ColorInt accentColor: Int): NotificationChannelCompat =
    NotificationChannelCompat.Builder(REMINDER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(stringProvider.getString(R.string.artise_notification_channel_reminders).ifEmpty { "Reminders" })
        .setDescription(stringProvider.getString(R.string.artise_notification_channel_reminders_description))
        .setSound(
            Settings.System.DEFAULT_NOTIFICATION_URI,
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build(),
        )
        .setVibrationEnabled(true)
        .setLightsEnabled(true)
        .setLightColor(accentColor)
        .build()
