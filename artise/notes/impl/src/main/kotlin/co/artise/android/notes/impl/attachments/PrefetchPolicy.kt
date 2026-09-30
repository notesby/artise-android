/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.attachments

import android.content.Context
import android.net.ConnectivityManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext

/** Whether photos may be downloaded ahead of time, before anyone opens the note. */
fun interface PrefetchPolicy {
    fun canPrefetch(): Boolean
}

/**
 * Only on Wi-Fi or another connection that isn't metered: many families are on prepaid mobile data, so the app
 * never spends it on photos nobody has asked to see. Opening a note still downloads its photos on any connection.
 */
@ContributesBinding(AppScope::class)
class DefaultPrefetchPolicy(
    @ApplicationContext private val context: Context,
) : PrefetchPolicy {
    override fun canPrefetch(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.activeNetwork != null && !connectivity.isActiveNetworkMetered
    }
}
