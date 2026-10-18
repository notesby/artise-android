/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.Backlink
import co.artise.android.notes.api.NoteLinks
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.SyncReport
import co.artise.android.notes.impl.ui.chats.NotesChatsEvent
import co.artise.android.notes.impl.ui.chats.NotesChatsPresenter
import co.artise.android.notes.impl.ui.chats.NotesSyncStatus
import co.artise.android.notes.impl.ui.folder.NotesFolderEntry
import co.artise.android.notes.impl.ui.folder.NotesFolderEvent
import co.artise.android.notes.impl.ui.folder.NotesFolderPresenter
import co.artise.android.notes.impl.ui.note.BacklinksState
import co.artise.android.notes.impl.ui.note.NoteDialog
import co.artise.android.notes.impl.ui.note.NoteEvent
import co.artise.android.notes.impl.ui.note.NotePresenter
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotesPresentersTest {
    private val room = RoomId("!familia:artise.co")
    private val offline = NotesException.Network(IllegalStateException("offline"))

    /** The chats list shows the phone's copy, then the server's list; chats without a name yet are hidden. */
    @Test
    fun `chats list refreshes from the server`() = runTest {
        val repository = FakeNotesRepository(
            chats = listOf(NotesChat(room, "Familia", "t1"), NotesChat(RoomId("!x:artise.co"), "", "t2")),
            refreshChatsResult = { Result.success(listOf(NotesChat(room, "Familia", "t1"), NotesChat(RoomId("!casa:artise.co"), "Casa", "t3"))) },
        )
        NotesChatsPresenter(repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            assertThat(state.chats.map { it.name }).containsExactly("Casa", "Familia").inOrder()
            assertThat(state.sync).isEqualTo(NotesSyncStatus.OK)
        }
    }

    /** Offline, the chats list keeps the phone's copy and says it's offline; pulling down tries again. */
    @Test
    fun `chats list offline keeps the cached list`() = runTest {
        val repository = FakeNotesRepository(chats = listOf(NotesChat(room, "Familia", "t1")), refreshChatsResult = { Result.failure(offline) })
        NotesChatsPresenter(repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            assertThat(state.chats.map { it.name }).containsExactly("Familia")
            assertThat(state.sync).isEqualTo(NotesSyncStatus.OFFLINE)
            repository.refreshChatsResult = { Result.success(listOf(NotesChat(room, "Familia", "t1"))) }
            state.eventSink(NotesChatsEvent.Refresh)
            assertThat(consumeItemsUntilPredicate { it.sync == NotesSyncStatus.OK && !it.isRefreshing }.last().chats).hasSize(1)
        }
    }

    /** A folder lists subfolders with note counts first, then its notes; photos aren't listed; unsent edits are marked. */
    @Test
    fun `folder groups subfolders and notes`() = runTest {
        val repository = FakeNotesRepository(
            chats = listOf(NotesChat(room, "Familia", "t1")),
            files = mutableMapOf(
                room to listOf(
                    aNote("Súper.md", hasLocalEdits = true),
                    aNote("Recetas/Mole.md"),
                    aNote("Recetas/Postres/Flan.md"),
                    aNote("Fotos/luna.jpg").copy(isNote = false),
                ),
            ),
            seenPrivacyNotice = true,
        )
        NotesFolderPresenter(room, "", {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing && it.title.isNotEmpty() }.last()
            assertThat(state.title).isEqualTo("Familia")
            assertThat(state.entries).containsExactly(
                NotesFolderEntry.Folder("Fotos", "Fotos", 0, 1),
                NotesFolderEntry.Folder("Recetas", "Recetas", 2, 0),
                NotesFolderEntry.Note("Súper", "Súper.md", hasLocalEdits = true),
            ).inOrder()
            assertThat(state.showPrivacyNotice).isFalse()
            assertThat(repository.syncCount).isEqualTo(1)
        }
    }

    /** Inside a folder, the title is the folder's name and paths stay full. */
    @Test
    fun `subfolder shows its own notes`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Recetas/Mole.md"), aNote("Recetas/Postres/Flan.md"))))
        NotesFolderPresenter(room, "Recetas", {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            assertThat(state.title).isEqualTo("Recetas")
            assertThat(state.entries).containsExactly(
                NotesFolderEntry.Folder("Postres", "Recetas/Postres", 1, 0),
                NotesFolderEntry.Note("Mole", "Recetas/Mole.md", hasLocalEdits = false),
            ).inOrder()
        }
    }

    /** The privacy notice shows once per account, at the top of a chat's notes, and dismissing it remembers that. */
    @Test
    fun `privacy notice shows once`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("a.md"))))
        NotesFolderPresenter(room, "", {}, repository).test {
            val state = consumeItemsUntilPredicate { it.showPrivacyNotice }.last()
            state.eventSink(NotesFolderEvent.DismissPrivacyNotice)
            assertThat(consumeItemsUntilPredicate { !it.showPrivacyNotice }.last().showPrivacyNotice).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.seenPrivacyNotice).isTrue()
    }

    /** Offline, the folder shows the phone's copy and the offline line. */
    @Test
    fun `folder offline shows the phone copy`() = runTest {
        val repository =
            FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("a.md"))), syncResult = { Result.failure(offline) }, seenPrivacyNotice = true)
        NotesFolderPresenter(room, "", {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            assertThat(state.sync).isEqualTo(NotesSyncStatus.OFFLINE)
            assertThat(state.entries).hasSize(1)
        }
    }

    /** A note not on the phone yet is downloaded by a sync before showing. */
    @Test
    fun `note not downloaded is fetched`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", content = null))))
        repository.syncResult = {
            repository.files[room] = listOf(aNote("Súper.md", content = "- leche"))
            Result.success(SyncReport(0, 0, 1, 0))
        }
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.title).isEqualTo("Súper")
            assertThat(state.content).isEqualTo("- leche")
        }
    }

    /** Tapping [[Mole]] opens Recetas/Mole.md; a link to a note that doesn't exist explains instead. */
    @Test
    fun `note links open notes or explain`() = runTest {
        val navigator = RecordingNoteNavigator()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"), aNote("Recetas/Mole.md"))))
        NotePresenter(room, "Súper.md", navigator, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.OpenNoteLink("Mole"))
            state.eventSink(NoteEvent.OpenNoteLink("Leche"))
            val withMissing = consumeItemsUntilPredicate { it.dialog != null }.last()
            assertThat(withMissing.dialog).isEqualTo(NoteDialog.MissingNote("Leche"))
            assertThat(navigator.opened).containsExactly("Recetas/Mole.md")
            withMissing.eventSink(NoteEvent.DismissDialog)
            assertThat(consumeItemsUntilPredicate { it.dialog == null }.last().dialog).isNull()
        }
    }

    /** Backlinks come from the server; offline the note still shows and says why backlinks are missing. */
    @Test
    fun `backlinks load or say offline`() = runTest {
        val repository = FakeNotesRepository(
            files = mutableMapOf(room to listOf(aNote("Súper.md"))),
            linksResult = { _, path -> Result.success(NoteLinks(path, emptyList(), listOf(Backlink("Recetas/Mole.md", "Ver [[Súper]]")))) },
        )
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            val state = consumeItemsUntilPredicate { it.backlinks is BacklinksState.Loaded }.last()
            assertThat((state.backlinks as BacklinksState.Loaded).backlinks.single().path).isEqualTo("Recetas/Mole.md")
            cancelAndIgnoreRemainingEvents()
        }
        repository.linksResult = { _, _ -> Result.failure(offline) }
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            assertThat(consumeItemsUntilPredicate { it.backlinks == BacklinksState.Offline }.last().content).isEqualTo("text")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
