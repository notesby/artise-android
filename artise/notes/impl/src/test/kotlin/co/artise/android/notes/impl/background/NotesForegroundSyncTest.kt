/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.background

import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class NotesForegroundSyncTest {
    private val family = RoomId("!familia:artise.co")
    private val work = RoomId("!trabajo:artise.co")
    private val familyState = MutableStateFlow(mapOf("" to """{"tree": "t1"}"""))
    private val trees = mutableListOf<Pair<RoomId, String>>()
    private val repository = FakeNotesRepository(chats = listOf(NotesChat(family, "Familia", "t1"), NotesChat(work, "Trabajo", "w1"))).apply {
        onTreeChangedResult = { roomId, tree ->
            trees += roomId to tree
            Result.success(null)
        }
    }
    private val client = FakeMatrixClient().apply {
        givenGetRoomResult(family, FakeBaseRoom(roomId = family, customStateEventsFlowLambda = { familyState }))
        givenGetRoomResult(work, FakeBaseRoom(roomId = work, customStateEventsFlowLambda = { MutableStateFlow(mapOf("" to """{"tree": "w1"}""")) }))
    }
    private val backgroundSync = FakeNotesBackgroundSync()

    private fun TestScope.follow() = launch {
        NotesForegroundSync(FakeAppForegroundStateService(), FakeNotesAccounts(emptyMap()), backgroundSync)
            .keepUpToDate(NotesAccount(client, repository))
    }

    /** Every chat on this phone is followed, not only one on screen: a changed tree in any of them is checked when allowed. */
    @Test
    fun `a changed chat syncs when allowed`() = runTest {
        val job = follow()
        runCurrent()
        familyState.value = mapOf("" to """{"tree": "t2"}""")
        runCurrent()
        assertThat(trees).containsExactly(family to "t1", work to "w1", family to "t2")
        job.cancel()
    }

    /** On a connection the setting doesn't allow, a changed tree waits (the next allowed check catches it). */
    @Test
    fun `a changed chat waits on a disallowed connection`() = runTest {
        backgroundSync.allowed = false
        val job = follow()
        runCurrent()
        familyState.value = mapOf("" to """{"tree": "t2"}""")
        runCurrent()
        assertThat(trees).isEmpty()
        job.cancel()
    }

    /** Every 5 minutes all chats sync when allowed; on a disallowed connection the check is skipped. */
    @Test
    fun `the 5 minute check follows the connection`() = runTest {
        val job = follow()
        runCurrent()
        advanceTimeBy(5.minutes)
        runCurrent()
        assertThat(repository.syncCount).isEqualTo(2)
        backgroundSync.allowed = false
        advanceTimeBy(5.minutes)
        runCurrent()
        assertThat(repository.syncCount).isEqualTo(2)
        backgroundSync.allowed = true
        advanceTimeBy(5.minutes)
        runCurrent()
        assertThat(repository.syncCount).isEqualTo(4)
        job.cancel()
    }
}
