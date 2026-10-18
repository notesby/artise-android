/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.api

import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * Per workspace, kept on this phone: the notification hours. Outside them the workspace's notifications still arrive, without sound.
 * Ari's reminders keep sounding.
 */
interface QuietHours {
    fun settings(sessionId: SessionId): Flow<NotificationHoursSettings>

    suspend fun set(sessionId: SessionId, settings: NotificationHoursSettings)

    /** True when a notification from [sessionId] should be silent now; a mention or reply passes when the person allows it. */
    fun isQuietNow(sessionId: SessionId, isMention: Boolean): Boolean
}
