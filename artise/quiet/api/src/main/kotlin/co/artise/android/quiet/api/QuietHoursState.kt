/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.runtime.Immutable

@Immutable
data class QuietHoursState(
    val settings: QuietHoursSettings,
    val isPickerVisible: Boolean,
    val eventSink: (QuietHoursEvent) -> Unit,
)

sealed interface QuietHoursEvent {
    data class Change(val settings: QuietHoursSettings) : QuietHoursEvent

    data object OpenPicker : QuietHoursEvent

    data object ClosePicker : QuietHoursEvent
}
