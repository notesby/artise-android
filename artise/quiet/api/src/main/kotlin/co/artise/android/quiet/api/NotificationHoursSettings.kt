/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import java.time.DayOfWeek

/** The same rules as iOS (ArtiseCore/NotificationHours.swift), so both phones sound at the same moments. */
enum class NotificationSchedule { EVERY_DAY, WEEKDAYS, CUSTOM }

/** From [startMinute] to [endMinute], in minutes after midnight. An earlier end goes past midnight; the same start and end is the whole day. */
@Immutable
data class HoursWindow(val startMinute: Int, val endMinute: Int) {
    val length: Int
        get() = Math.floorMod(endMinute - startMinute, MINUTES_PER_DAY).let { if (it == 0) MINUTES_PER_DAY else it }

    /** Whether [minute] falls in these hours on the day they start ([daysLater] = 0) or the day after (1). */
    fun contains(minute: Int, daysLater: Int): Boolean = minute + daysLater * MINUTES_PER_DAY - startMinute in 0 until length

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** When a workspace's notifications may sound. Off means they always do. */
@Immutable
data class NotificationHoursSettings(
    val enabled: Boolean = false,
    val schedule: NotificationSchedule = NotificationSchedule.EVERY_DAY,
    val window: HoursWindow = HoursWindow(7 * 60, 22 * 60),
    /** For [NotificationSchedule.CUSTOM]; a missing day is off, so it has no sound all day. */
    val days: ImmutableMap<DayOfWeek, HoursWindow> = persistentMapOf(),
    val allowMentions: Boolean = true,
) {
    /** The hours that start on [day], or null when that day has none. */
    fun windowFor(day: DayOfWeek): HoursWindow? = when (schedule) {
        NotificationSchedule.EVERY_DAY -> window
        NotificationSchedule.WEEKDAYS -> window.takeIf { day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY }
        NotificationSchedule.CUSTOM -> days[day]
    }

    /** Whether a notification may sound on [day] at [minute] after midnight: today's hours, or yesterday's that go past midnight. */
    fun soundsAt(day: DayOfWeek, minute: Int, isMention: Boolean): Boolean {
        if (!enabled || isMention && allowMentions) return true
        return windowFor(day)?.contains(minute, daysLater = 0) == true || windowFor(day.minus(1))?.contains(minute, daysLater = 1) == true
    }

    /** Custom starts from the hours already shown: every day from Every day, Monday to Friday from Monday to Friday. */
    fun withSchedule(next: NotificationSchedule): NotificationHoursSettings = when {
        next == schedule -> this
        next == NotificationSchedule.CUSTOM -> copy(
            schedule = next,
            days = DayOfWeek.entries.mapNotNull { day -> windowFor(day)?.let { day to it } }.toMap().toImmutableMap(),
        )
        else -> copy(schedule = next)
    }

    /** A day switched on gets the Every day / Monday to Friday hours; switched off, it has no sound all day. */
    fun withDay(day: DayOfWeek, on: Boolean): NotificationHoursSettings =
        copy(days = (if (on) days + (day to window) else days - day).toImmutableMap())

    /** [day] null changes the Every day / Monday to Friday hours. */
    fun withTime(day: DayOfWeek?, isStart: Boolean, minute: Int): NotificationHoursSettings {
        fun HoursWindow.set() = if (isStart) copy(startMinute = minute) else copy(endMinute = minute)
        if (day == null) return copy(window = window.set())
        val current = days[day] ?: return this
        return copy(days = (days + (day to current.set())).toImmutableMap())
    }

    companion object {
        /** Saved quiet hours, at the same times: they sound from the old end to the old start. "All weekend" becomes Monday to Friday. */
        fun fromQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int, wholeWeekend: Boolean, allowMentions: Boolean) =
            NotificationHoursSettings(
                enabled = enabled,
                schedule = if (wholeWeekend) NotificationSchedule.WEEKDAYS else NotificationSchedule.EVERY_DAY,
                window = HoursWindow(startMinute = endMinute, endMinute = startMinute),
                allowMentions = allowMentions,
            )
    }
}
