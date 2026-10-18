/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import co.artise.android.notes.api.NotesBackgroundNetwork
import co.artise.android.notes.api.NotesBackgroundSettings
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultNotesBackgroundSyncTest {
    private val room = RoomId("!familia:artise.co")
    private val repository = FakeNotesRepository(chats = listOf(NotesChat(room, "Familia", "t1")))
    private val settings = object : NotesBackgroundSettings {
        override val network = MutableStateFlow(NotesBackgroundNetwork.WIFI_ONLY)

        override fun set(network: NotesBackgroundNetwork) {
            this.network.value = network
        }
    }
    private var connection = NotesConnection(isConnected = true, isUnmetered = false, isDataSaverOn = false)
    private val sync = DefaultNotesBackgroundSync(
        settings = settings,
        connection = { connection },
        accounts = FakeNotesAccounts(mapOf(A_SESSION_ID to NotesAccount(FakeMatrixClient(), repository))),
    )

    /** On mobile data with "Solo con Wi-Fi" nothing syncs; after choosing "Wi-Fi y datos móviles" it does. Data Saver stops it again. */
    @Test
    fun `background syncs follow the setting and data saver`() = runTest {
        assertThat(sync.syncChat(A_SESSION_ID, room)).isFalse()
        sync.syncAll()
        assertThat(repository.syncCount).isEqualTo(0)

        settings.set(NotesBackgroundNetwork.WIFI_AND_MOBILE)
        assertThat(sync.syncChat(A_SESSION_ID, room)).isTrue()
        sync.syncAll()
        assertThat(repository.syncCount).isEqualTo(2)

        connection = connection.copy(isDataSaverOn = true)
        sync.syncAll()
        assertThat(repository.syncCount).isEqualTo(2)
    }
}
