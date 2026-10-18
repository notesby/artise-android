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
import co.artise.android.notes.api.NotesPush
import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
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
    private val otherRepository = FakeNotesRepository(chats = listOf(NotesChat(RoomId("!otro:acme.artise.co"), "Otro", "o1")))
    private val scheduler = FakeNotesSyncScheduler()
    private val sync = DefaultNotesBackgroundSync(
        settings = settings,
        connection = { connection },
        accounts = FakeNotesAccounts(
            mapOf(
                A_SESSION_ID to NotesAccount(FakeMatrixClient(), repository),
                A_SESSION_ID_2 to NotesAccount(FakeMatrixClient(sessionId = A_SESSION_ID_2), otherRepository),
            )
        ),
        scheduler = scheduler,
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

    /** A notes push names only the chat: it syncs in the account that has that chat's notes, and only when allowed. */
    @Test
    fun `a pushed chat syncs in the account that has it`() = runTest {
        settings.set(NotesBackgroundNetwork.WIFI_AND_MOBILE)
        assertThat(sync.syncChatInAnyAccount(room)).isTrue()
        assertThat(repository.syncCount).isEqualTo(1)
        assertThat(otherRepository.syncCount).isEqualTo(0)
        assertThat(sync.syncChatInAnyAccount(RoomId("!desconocido:artise.co"))).isFalse()

        settings.set(NotesBackgroundNetwork.WIFI_ONLY)
        assertThat(sync.syncChatInAnyAccount(room)).isFalse()
        assertThat(repository.syncCount).isEqualTo(1)
    }

    /** The push itself only queues a job for that chat, with the connection the setting allows; the job waits for it. */
    @Test
    fun `a push queues a job with the setting's connection`() = runTest {
        assertThat(sync.onNotesPush(room)).isTrue()
        settings.set(NotesBackgroundNetwork.WIFI_AND_MOBILE)
        sync.onNotesPush(room)
        assertThat(scheduler.chats).containsExactly(room to NotesBackgroundNetwork.WIFI_ONLY, room to NotesBackgroundNetwork.WIFI_AND_MOBILE).inOrder()
        assertThat(repository.syncCount).isEqualTo(0)
    }

    /** A push for a chat whose notes aren't on this phone is ignored: no job, no sync. */
    @Test
    fun `a push for a chat not on the phone is ignored`() = runTest {
        assertThat(sync.onNotesPush(RoomId("!desconocido:artise.co"))).isFalse()
        assertThat(scheduler.chats).isEmpty()
    }

    /** Only a data message with artise_notes_room_id is a notes push; a regular message push (room_id, event_id) isn't. */
    @Test
    fun `notes pushes are told apart from message pushes`() {
        assertThat(NotesPush.roomIdOf(mapOf("artise_notes_room_id" to "!familia:artise.co", "event_id" to "\$e", "prio" to "normal")))
            .isEqualTo(room)
        assertThat(NotesPush.roomIdOf(mapOf("room_id" to "!familia:artise.co", "event_id" to "\$e"))).isNull()
        assertThat(NotesPush.roomIdOf(mapOf("artise_notes_room_id" to "not a room"))).isNull()
    }
}
