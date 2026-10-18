/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/** A From or Until being picked: [day] null for the Every day / Monday to Friday hours. */
@Immutable
data class HoursField(val day: DayOfWeek?, val isStart: Boolean)

@Immutable
data class NotificationHoursState(
    val settings: NotificationHoursSettings,
    val picking: HoursField?,
    val eventSink: (NotificationHoursEvent) -> Unit,
)

sealed interface NotificationHoursEvent {
    data class SetEnabled(val enabled: Boolean) : NotificationHoursEvent

    data class SetSchedule(val schedule: NotificationSchedule) : NotificationHoursEvent

    data class SetDay(val day: DayOfWeek, val on: Boolean) : NotificationHoursEvent

    data class SetAllowMentions(val allow: Boolean) : NotificationHoursEvent

    data class Pick(val field: HoursField) : NotificationHoursEvent

    data class SetTime(val field: HoursField, val minute: Int) : NotificationHoursEvent

    data object ClosePicker : NotificationHoursEvent
}
