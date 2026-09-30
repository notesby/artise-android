/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/** How long a message is held before its actions and reactions open, when the phone uses the standard delay. */
internal const val QUICK_LONG_PRESS_MILLIS = 250L

/**
 * Artise: in the timeline, holding a message opens its reactions sooner than Android's standard 400 ms.
 * Someone who chose a longer touch-and-hold delay in the phone's accessibility settings keeps theirs.
 */
@Composable
internal fun rememberQuickLongPressViewConfiguration(): ViewConfiguration {
    val base = LocalViewConfiguration.current
    return remember(base) { QuickLongPressViewConfiguration(base) }
}

internal class QuickLongPressViewConfiguration(private val base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long
        get() = if (base.longPressTimeoutMillis <= STANDARD_LONG_PRESS_MILLIS) {
            minOf(base.longPressTimeoutMillis, QUICK_LONG_PRESS_MILLIS)
        } else {
            base.longPressTimeoutMillis
        }

    private companion object {
        /** Android's default touch-and-hold delay; longer means the person chose it for accessibility. */
        const val STANDARD_LONG_PRESS_MILLIS = 400L
    }
}
