/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import co.artise.android.quiet.api.HoursField
import co.artise.android.quiet.api.HoursWindow
import co.artise.android.quiet.api.NotificationHoursEvent
import co.artise.android.quiet.api.NotificationSchedule
import co.artise.android.quiet.test.FakeQuietHours
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek

class NotificationHoursPresenterTest {
    private val quietHours = FakeQuietHours()

    private fun createNotificationHoursPresenter() = NotificationHoursPresenter(FakeMatrixClient(), quietHours)

    /** Off at first; turning it on saves Every day 07:00–22:00. */
    @Test
    fun `turn on`() = runTest {
        createNotificationHoursPresenter().test {
            val initial = awaitItem()
            assertThat(initial.settings.enabled).isFalse()
            assertThat(initial.picking).isNull()
            initial.eventSink(NotificationHoursEvent.SetEnabled(true))
            val on = awaitItem()
            assertThat(on.settings.enabled).isTrue()
            assertThat(on.settings.schedule).isEqualTo(NotificationSchedule.EVERY_DAY)
            assertThat(quietHours.settings.value.window).isEqualTo(HoursWindow(7 * 60, 22 * 60))
        }
    }

    /** Monday to Friday, then Custom: the weekend starts off, and Sunday switched on gets the shown hours. */
    @Test
    fun `days and custom`() = runTest {
        createNotificationHoursPresenter().test {
            awaitItem().eventSink(NotificationHoursEvent.SetSchedule(NotificationSchedule.WEEKDAYS))
            awaitItem().eventSink(NotificationHoursEvent.SetSchedule(NotificationSchedule.CUSTOM))
            val custom = awaitItem()
            assertThat(custom.settings.days.keys).doesNotContain(DayOfWeek.SUNDAY)
            custom.eventSink(NotificationHoursEvent.SetDay(DayOfWeek.SUNDAY, on = true))
            assertThat(awaitItem().settings.days[DayOfWeek.SUNDAY]).isEqualTo(HoursWindow(7 * 60, 22 * 60))
        }
    }

    /** Picking a time opens the picker for that field and saves the minute; closing hides it. Mentions can be turned off. */
    @Test
    fun `pick a time and mentions`() = runTest {
        createNotificationHoursPresenter().test {
            val field = HoursField(day = null, isStart = true)
            awaitItem().eventSink(NotificationHoursEvent.Pick(field))
            val picking = awaitItem()
            assertThat(picking.picking).isEqualTo(field)
            picking.eventSink(NotificationHoursEvent.SetTime(field, 9 * 60 + 30))
            assertThat(awaitItem().settings.window).isEqualTo(HoursWindow(9 * 60 + 30, 22 * 60))
            picking.eventSink(NotificationHoursEvent.ClosePicker)
            val closed = awaitItem()
            assertThat(closed.picking).isNull()
            closed.eventSink(NotificationHoursEvent.SetAllowMentions(false))
            assertThat(awaitItem().settings.allowMentions).isFalse()
        }
    }
}
