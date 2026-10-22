/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import android.os.Build
import androidx.work.NetworkType
import co.artise.android.notes.api.NotesBackgroundNetwork
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class NotesBackgroundPolicyTest {
    private val wifi = NotesConnection(isConnected = true, isUnmetered = true, isDataSaverOn = false)
    private val mobile = NotesConnection(isConnected = true, isUnmetered = false, isDataSaverOn = false)
    private val offline = NotesConnection(isConnected = false, isUnmetered = false, isDataSaverOn = false)

    /** "Solo con Wi-Fi" allows Wi-Fi only; "Wi-Fi y datos móviles" both; nothing works offline. */
    @Test
    fun `the setting decides the connection`() {
        assertThat(NotesBackgroundNetwork.WIFI_ONLY.allows(wifi)).isTrue()
        assertThat(NotesBackgroundNetwork.WIFI_ONLY.allows(mobile)).isFalse()
        assertThat(NotesBackgroundNetwork.WIFI_AND_MOBILE.allows(wifi)).isTrue()
        assertThat(NotesBackgroundNetwork.WIFI_AND_MOBILE.allows(mobile)).isTrue()
        assertThat(NotesBackgroundNetwork.WIFI_AND_MOBILE.allows(offline)).isFalse()
    }

    /** Data Saver on blocks background updates with either choice, on Wi-Fi too. */
    @Test
    fun `data saver blocks both`() {
        NotesBackgroundNetwork.entries.forEach { network ->
            assertThat(network.allows(wifi.copy(isDataSaverOn = true))).isFalse()
            assertThat(network.allows(mobile.copy(isDataSaverOn = true))).isFalse()
        }
    }

    /** The job while the app is closed waits for an unmetered connection with "Solo con Wi-Fi", and for any with the other. */
    @Test
    fun `the worker's constraint follows the setting`() {
        assertThat(NotesSyncScheduler.constraintsFor(NotesBackgroundNetwork.WIFI_ONLY).requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
        assertThat(NotesSyncScheduler.constraintsFor(NotesBackgroundNetwork.WIFI_AND_MOBILE).requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }
}
