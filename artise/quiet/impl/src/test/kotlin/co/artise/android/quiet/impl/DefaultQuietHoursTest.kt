/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import android.content.Context
import androidx.core.content.edit
import co.artise.android.quiet.api.HoursWindow
import co.artise.android.quiet.api.NotificationHoursSettings
import co.artise.android.quiet.api.NotificationSchedule
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class DefaultQuietHoursTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("artise_quiet_hours", Context.MODE_PRIVATE)

    /** Quiet hours saved by the earlier version (22:00–07:00 + all weekend) open as Monday to Friday 07:00–22:00, then save in the new format. */
    @Test
    fun `saved quiet hours carry over`() = runTest {
        preferences.edit {
            putString("settings", """{"${A_SESSION_ID.value}": {"enabled": true, "start": 1320, "end": 420, "weekend": true, "mentions": true}}""")
        }
        val quietHours = DefaultQuietHours(context, FakeSystemClock(), testCoroutineDispatchers())
        val carried = quietHours.settings(A_SESSION_ID).first()
        assertThat(carried).isEqualTo(NotificationHoursSettings(enabled = true, schedule = NotificationSchedule.WEEKDAYS, window = HoursWindow(420, 1320)))

        quietHours.set(A_SESSION_ID, carried.copy(allowMentions = false))
        assertThat(preferences.contains("settings")).isFalse()
        val reopened = DefaultQuietHours(context, FakeSystemClock(), testCoroutineDispatchers())
        assertThat(reopened.settings(A_SESSION_ID).first().allowMentions).isFalse()
    }

    /** Each workspace keeps its own hours; one never set is off, so it always sounds. */
    @Test
    fun `saved per workspace`() = runTest {
        val quietHours = DefaultQuietHours(context, FakeSystemClock(), testCoroutineDispatchers())
        val custom = NotificationHoursSettings(enabled = true).withSchedule(NotificationSchedule.CUSTOM).withDay(DayOfWeek.SUNDAY, on = false)
        quietHours.set(A_SESSION_ID, custom)
        val reopened = DefaultQuietHours(context, FakeSystemClock(), testCoroutineDispatchers())
        assertThat(reopened.settings(A_SESSION_ID).first()).isEqualTo(custom)
        assertThat(reopened.settings(A_SESSION_ID_2).first()).isEqualTo(NotificationHoursSettings())
        assertThat(reopened.isQuietNow(A_SESSION_ID_2, isMention = false)).isFalse()
    }
}
