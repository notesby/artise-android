/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.sync

import co.artise.android.notes.api.SyncReport
import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotesLiveUpdatesTest {
    private val room = RoomId("!familia:artise.co")

    /** The tree hash is read from the state content; bad or empty content gives nothing. */
    @Test
    fun `tree is read from the state content`() {
        assertThat(NotesLiveUpdates.treeOf("""{"tree": "9f1c"}""")).isEqualTo("9f1c")
        assertThat(NotesLiveUpdates.treeOf("""{"tree": ""}""")).isNull()
        assertThat(NotesLiveUpdates.treeOf("not json")).isNull()
        assertThat(NotesLiveUpdates.treeOf(null)).isNull()
    }

    /** Each new tree hash from the room state is passed on once; the same hash again is ignored. */
    @Test
    fun `new tree hashes trigger a check`() = runTest {
        val state = MutableStateFlow(mapOf("" to """{"tree": "t1"}"""))
        val trees = mutableListOf<String>()
        val repository = FakeNotesRepository()
        repository.onTreeChangedResult = { _, tree ->
            trees += tree
            Result.success(null)
        }
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(room, FakeBaseRoom(roomId = room, customStateEventsFlowLambda = { state }))
        }
        val job = launch { NotesLiveUpdates(client, repository).keepUpToDate(listOf(room)) }
        advanceUntilIdle()
        state.value = mapOf("" to """{"tree": "t1"}""")
        advanceUntilIdle()
        state.value = mapOf("" to """{"tree": "t2"}""")
        advanceUntilIdle()
        assertThat(trees).containsExactly("t1", "t2").inOrder()
        assertThat(repository.syncCount).isEqualTo(0)
        job.cancel()
    }

    /** A chat without the state event gets a single ETag check. */
    @Test
    fun `chat without state gets one check`() = runTest {
        val repository = FakeNotesRepository(syncResult = { Result.success(SyncReport(0, 0, 0, 0)) })
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(room, FakeBaseRoom(roomId = room, customStateEventsFlowLambda = { MutableStateFlow(emptyMap()) }))
        }
        val job = launch { NotesLiveUpdates(client, repository).keepUpToDate(listOf(room)) }
        advanceUntilIdle()
        assertThat(repository.syncCount).isEqualTo(1)
        job.cancel()
    }
}
