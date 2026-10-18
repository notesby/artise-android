/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import co.artise.android.quiet.api.HoursWindow
import co.artise.android.quiet.api.NotificationHoursSettings
import co.artise.android.quiet.api.NotificationSchedule
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The same cases as iOS's NotificationHoursTests, so both phones sound at the same moments. */
class NotificationHoursTest {
    private val day = NotificationHoursSettings(enabled = true, window = HoursWindow(7 * 60, 22 * 60))

    // Friday 16 Oct 2026; Saturday 17, Sunday 18, Monday 19.
    private fun at(date: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, date, hour, minute, 0, 0, ZoneOffset.UTC)

    private fun NotificationHoursSettings.sounds(date: Int, hour: Int, minute: Int = 0, isMention: Boolean = false) =
        !DefaultQuietHours.isQuiet(this, at(date, hour, minute), isMention)

    /** Every day 07:00–22:00 sounds from 07:00 up to 21:59, weekend included; off always sounds. */
    @Test
    fun `every day`() {
        assertThat(day.sounds(16, 7)).isTrue()
        assertThat(day.sounds(16, 21, 59)).isTrue()
        assertThat(day.sounds(16, 22)).isFalse()
        assertThat(day.sounds(16, 6, 59)).isFalse()
        assertThat(day.sounds(18, 12)).isTrue()
        assertThat(day.copy(enabled = false).sounds(16, 23)).isTrue()
    }

    /** Monday to Friday 09:00–18:00: Saturday and Sunday have no sound all day. */
    @Test
    fun `monday to friday`() {
        val weekdays = day.copy(schedule = NotificationSchedule.WEEKDAYS, window = HoursWindow(9 * 60, 18 * 60))
        assertThat(weekdays.sounds(16, 12)).isTrue()
        assertThat(weekdays.sounds(19, 9)).isTrue()
        assertThat(weekdays.sounds(17, 12)).isFalse()
        assertThat(weekdays.sounds(18, 12)).isFalse()
    }

    /** Custom: a day that's on uses its own hours; a day that's off has no sound all day. */
    @Test
    fun `custom with a day off`() {
        val custom = day.copy(schedule = NotificationSchedule.CUSTOM, days = persistentMapOf(DayOfWeek.SUNDAY to HoursWindow(10 * 60, 12 * 60)))
        assertThat(custom.sounds(18, 10)).isTrue()
        assertThat(custom.sounds(18, 12)).isFalse()
        assertThat(custom.sounds(17, 11)).isFalse()
        assertThat(custom.sounds(19, 11)).isFalse()
    }

    /** Friday 18:00–02:00 belongs to Friday: it rings Saturday 01:59 even with Monday to Friday, but not Saturday 23:00. */
    @Test
    fun `past midnight belongs to the day it starts`() {
        val evenings = day.copy(schedule = NotificationSchedule.WEEKDAYS, window = HoursWindow(18 * 60, 2 * 60))
        assertThat(evenings.sounds(16, 18)).isTrue()
        assertThat(evenings.sounds(17, 1, 59)).isTrue()
        assertThat(evenings.sounds(17, 2)).isFalse()
        assertThat(evenings.sounds(17, 23)).isFalse()
        val nights = day.copy(window = HoursWindow(22 * 60, 2 * 60))
        assertThat(nights.sounds(16, 23)).isTrue()
        assertThat(nights.sounds(16, 3)).isFalse()
    }

    /** The same From and Until is the whole day. */
    @Test
    fun `same from and until is the whole day`() {
        val allDay = day.copy(window = HoursWindow(8 * 60, 8 * 60))
        assertThat(allDay.window.length).isEqualTo(24 * 60)
        assertThat(allDay.sounds(16, 8)).isTrue()
        assertThat(allDay.sounds(16, 7, 59)).isTrue()
        assertThat(allDay.sounds(16, 23, 59)).isTrue()
    }

    /** Custom starts from the hours shown: all seven days from Every day, weekend off from Monday to Friday. A day switched on gets those hours. */
    @Test
    fun `switching to custom keeps the hours shown`() {
        val window = HoursWindow(9 * 60, 18 * 60)
        val fromEveryDay = day.copy(window = window).withSchedule(NotificationSchedule.CUSTOM)
        assertThat(fromEveryDay.days.keys).containsExactlyElementsIn(DayOfWeek.entries)
        assertThat(fromEveryDay.days.values.toSet()).containsExactly(window)
        val fromWeekdays = day.copy(schedule = NotificationSchedule.WEEKDAYS, window = window).withSchedule(NotificationSchedule.CUSTOM)
        assertThat(fromWeekdays.days.keys)
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val sundayOn = fromWeekdays.withDay(DayOfWeek.SUNDAY, on = true)
        assertThat(sundayOn.days[DayOfWeek.SUNDAY]).isEqualTo(window)
        val sundayLater = sundayOn.withTime(DayOfWeek.SUNDAY, isStart = true, minute = 10 * 60)
        assertThat(sundayLater.days[DayOfWeek.SUNDAY]).isEqualTo(HoursWindow(10 * 60, 18 * 60))
        assertThat(sundayLater.days[DayOfWeek.MONDAY]).isEqualTo(window)
        assertThat(sundayLater.withDay(DayOfWeek.SUNDAY, on = false).days).doesNotContainKey(DayOfWeek.SUNDAY)
    }

    /** Mentions and replies sound anytime when allowed (the default), and follow the hours when not. */
    @Test
    fun `mentions sound when allowed`() {
        assertThat(day.sounds(16, 23, isMention = true)).isTrue()
        assertThat(day.copy(allowMentions = false).sounds(16, 23, isMention = true)).isFalse()
    }

    /** Old quiet hours 22:00–07:00 with all weekend become Monday to Friday 07:00–22:00; without the weekend, Every day. */
    @Test
    fun `old quiet hours carry over at the same times`() {
        val carried = NotificationHoursSettings.fromQuietHours(
            enabled = true,
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            wholeWeekend = true,
            allowMentions = false,
        )
        assertThat(carried).isEqualTo(
            NotificationHoursSettings(enabled = true, schedule = NotificationSchedule.WEEKDAYS, window = HoursWindow(7 * 60, 22 * 60), allowMentions = false)
        )
        val everyDay = NotificationHoursSettings.fromQuietHours(
            enabled = false,
            startMinute = 23 * 60,
            endMinute = 7 * 60,
            wholeWeekend = false,
            allowMentions = true,
        )
        assertThat(everyDay.schedule).isEqualTo(NotificationSchedule.EVERY_DAY)
        assertThat(everyDay.window).isEqualTo(HoursWindow(7 * 60, 23 * 60))
        assertThat(everyDay.enabled).isFalse()
    }

    /** First turned on: Every day, 07:00 to 22:00, mentions sound. */
    @Test
    fun `the default`() {
        val settings = NotificationHoursSettings()
        assertThat(settings.schedule).isEqualTo(NotificationSchedule.EVERY_DAY)
        assertThat(settings.window).isEqualTo(HoursWindow(7 * 60, 22 * 60))
        assertThat(settings.allowMentions).isTrue()
    }
}
