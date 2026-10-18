/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver

/** Forgets Artise ID's tokens when the person signs out of their last workspace. Registered at app start: sign-out can happen anywhere. */
@ContributesIntoSet(AppScope::class)
class ArtiseIdSessionCleanup(
    private val artiseIdAuth: ArtiseIdAuth,
    private val sessionObserver: SessionObserver,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                if (wasLastSession) artiseIdAuth.signOut()
            }
        })
    }
}
