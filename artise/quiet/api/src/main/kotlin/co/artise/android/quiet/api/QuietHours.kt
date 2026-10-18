/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

/** A workspace's quiet hours, from [startMinute] to [endMinute] (minutes after midnight; overnight when start is after end). */
@Immutable
data class QuietHoursSettings(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val wholeWeekend: Boolean = false,
    val allowMentions: Boolean = true,
)

/** Per workspace, kept on this phone. During quiet hours its notifications still arrive, without sound. Ari's reminders keep sounding. */
interface QuietHours {
    fun settings(sessionId: SessionId): Flow<QuietHoursSettings>

    suspend fun set(sessionId: SessionId, settings: QuietHoursSettings)

    /** True when a notification from [sessionId] should be silent now; a mention or reply passes when the person allows it. */
    fun isQuietNow(sessionId: SessionId, isMention: Boolean): Boolean
}
