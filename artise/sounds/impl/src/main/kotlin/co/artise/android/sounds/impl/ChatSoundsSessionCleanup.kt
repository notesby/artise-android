/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.sounds.impl

import android.app.Activity
import co.artise.android.sounds.api.ChatSounds
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver

/** Signing out of a workspace removes its chats' sound channels; "Tus sonidos" stay until removed. */
@ContributesIntoSet(AppScope::class)
class ChatSoundsSessionCleanup(
    private val chatSounds: ChatSounds,
    private val sessionObserver: SessionObserver,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                chatSounds.clear(SessionId(userId))
            }
        })
    }
}
