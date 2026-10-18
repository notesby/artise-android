/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl

import co.artise.android.quiet.api.QuietHoursSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class QuietHoursTest {
    private val night = QuietHoursSettings(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)

    // Friday 16 Oct 2026, and Saturday 17 Oct.
    private fun friday(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 16, hour, minute, 0, 0, ZoneOffset.UTC)

    private fun saturday(hour: Int) = ZonedDateTime.of(2026, 10, 17, hour, 0, 0, 0, ZoneOffset.UTC)

    /** 22:00–07:00 wraps midnight: quiet late at night and early in the morning, not during the day; off means never quiet. */
    @Test
    fun `an overnight window`() {
        assertThat(DefaultQuietHours.isQuiet(night, friday(23), isMention = false)).isTrue()
        assertThat(DefaultQuietHours.isQuiet(night, friday(6, 59), isMention = false)).isTrue()
        assertThat(DefaultQuietHours.isQuiet(night, friday(7), isMention = false)).isFalse()
        assertThat(DefaultQuietHours.isQuiet(night, friday(12), isMention = false)).isFalse()
        assertThat(DefaultQuietHours.isQuiet(night.copy(enabled = false), friday(23), isMention = false)).isFalse()
    }

    /** Mentions and replies sound when allowed; "Todo el fin de semana" makes Saturday quiet all day. */
    @Test
    fun `mentions and weekends`() {
        assertThat(DefaultQuietHours.isQuiet(night, friday(23), isMention = true)).isFalse()
        assertThat(DefaultQuietHours.isQuiet(night.copy(allowMentions = false), friday(23), isMention = true)).isTrue()
        assertThat(DefaultQuietHours.isQuiet(night, saturday(12), isMention = false)).isFalse()
        assertThat(DefaultQuietHours.isQuiet(night.copy(wholeWeekend = true), saturday(12), isMention = false)).isTrue()
    }
}
