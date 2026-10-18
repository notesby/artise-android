/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import co.artise.android.notes.api.NotesBackgroundNetwork
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext

/** The phone's connection right now, as far as background updates care. */
data class NotesConnection(
    val isConnected: Boolean,
    /** Wi-Fi or another connection that isn't billed by the amount used. */
    val isUnmetered: Boolean,
    /** Data Saver is on for this app: background data waits until the person opens Notas. */
    val isDataSaverOn: Boolean,
)

/** Whether background updates may use [connection]: never with Data Saver on, and only unmetered with "Solo con Wi-Fi". */
fun NotesBackgroundNetwork.allows(connection: NotesConnection): Boolean = when {
    !connection.isConnected || connection.isDataSaverOn -> false
    this == NotesBackgroundNetwork.WIFI_ONLY -> connection.isUnmetered
    else -> true
}

fun interface NotesConnectionMonitor {
    fun current(): NotesConnection
}

@ContributesBinding(AppScope::class)
class DefaultNotesConnectionMonitor(
    @ApplicationContext private val context: Context,
) : NotesConnectionMonitor {
    override fun current(): NotesConnection {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NotesConnection(isConnected = false, isUnmetered = false, isDataSaverOn = false)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        return NotesConnection(
            isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            isUnmetered = capabilities != null && !connectivity.isActiveNetworkMetered,
            isDataSaverOn = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
        )
    }
}
