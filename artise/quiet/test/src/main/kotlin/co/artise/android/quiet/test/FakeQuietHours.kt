/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.test

import co.artise.android.quiet.api.QuietHours
import co.artise.android.quiet.api.QuietHoursSettings
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQuietHours(var quiet: Boolean = false) : QuietHours {
    val settings = MutableStateFlow(QuietHoursSettings())

    override fun settings(sessionId: SessionId) = settings

    override suspend fun set(sessionId: SessionId, settings: QuietHoursSettings) {
        this.settings.value = settings
    }

    override fun isQuietNow(sessionId: SessionId, isMention: Boolean) = quiet
}
