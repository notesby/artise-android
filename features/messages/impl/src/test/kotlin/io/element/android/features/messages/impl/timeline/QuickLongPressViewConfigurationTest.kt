/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import androidx.compose.ui.platform.ViewConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuickLongPressViewConfigurationTest {
    private fun phoneWith(longPress: Long) = object : ViewConfiguration {
        override val longPressTimeoutMillis = longPress
        override val doubleTapTimeoutMillis = 300L
        override val doubleTapMinTimeMillis = 40L
        override val touchSlop = 8f
    }

    /** With Android's standard delay, holding a message opens its reactions after 250 ms instead of 400 ms. */
    @Test
    fun `standard delay becomes quicker`() {
        assertThat(QuickLongPressViewConfiguration(phoneWith(400L)).longPressTimeoutMillis).isEqualTo(250L)
    }

    /** A delay already shorter than ours is kept. */
    @Test
    fun `shorter delay is kept`() {
        assertThat(QuickLongPressViewConfiguration(phoneWith(200L)).longPressTimeoutMillis).isEqualTo(200L)
    }

    /** Someone who set a longer touch-and-hold delay for accessibility keeps it. */
    @Test
    fun `accessibility delay is respected`() {
        assertThat(QuickLongPressViewConfiguration(phoneWith(1000L)).longPressTimeoutMillis).isEqualTo(1000L)
    }

    /** Everything else (touch slop, double tap) comes from the phone. */
    @Test
    fun `other settings pass through`() {
        assertThat(QuickLongPressViewConfiguration(phoneWith(400L)).touchSlop).isEqualTo(8f)
    }
}
