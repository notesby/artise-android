/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import co.artise.android.notes.api.ChangeAuthor
import co.artise.android.notes.api.ChangedFile
import co.artise.android.notes.api.FileChange
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.Note
import co.artise.android.notes.api.NoteVersion
import co.artise.android.notes.api.NotesChange
import co.artise.android.notes.api.NotesChangesPage
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.impl.R
import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

class NotesHistoryPresentersTest {
    private val room = RoomId("!familia:artise.co")
    private val offline = NotesException.Network(IllegalStateException("offline"))

    // Days by UTC date and times as HH:mm, so the tests don't depend on the machine's time zone or language.
    private val dateFormatter = FakeDateFormatter { millis, mode, _ ->
        val time = Instant.ofEpochMilli(millis ?: 0).atOffset(ZoneOffset.UTC)
        when (mode) {
            DateFormatterMode.Day -> time.toLocalDate().toString()
            DateFormatterMode.TimeOnly -> "%02d:%02d".format(time.hour, time.minute)
            else -> time.toString()
        }
    }

    /** Changes come grouped by day, newest first, each with its time and label. */
    @Test
    fun `changes are grouped by day with time and label`() = runTest {
        val repository = FakeNotesRepository(
            changeHistoryResult = { _, _ ->
                Result.success(
                    NotesChangesPage(
                        listOf(
                            aChange("c3", "2026-10-10T18:02:00Z", how = "automation", detail = "#3"),
                            aChange("c2", "2026-10-10T09:15:00Z", how = "app"),
                            aChange("c1", "2026-10-09T21:40:00Z", how = "sync", who = null, by = "synced device"),
                        ),
                        more = false,
                    )
                )
            },
        )
        NotesChangesPresenter(room, repository, dateFormatter).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.days.map { it.label }).containsExactly("2026-10-10", "2026-10-09").inOrder()
            assertThat(state.days[0].entries.map { it.time }).containsExactly("18:02", "09:15").inOrder()
            assertThat(state.days[0].entries[0].label).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_automation, listOf("Ana", "#3")))
            assertThat(state.days[1].entries.single().label).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_sync))
            assertThat(state.hasMore).isFalse()
        }
    }

    /** With `more`, the next page is asked for with the last commit as `before`, and added to the end. */
    @Test
    fun `load more asks for the page before the last change`() = runTest {
        val repository = FakeNotesRepository(
            changeHistoryResult = { _, before ->
                Result.success(
                    when (before) {
                        null -> NotesChangesPage(listOf(aChange("c3", "2026-10-10T18:00:00Z"), aChange("c2", "2026-10-10T09:00:00Z")), more = true)
                        // c2 again: the page boundary moved; it's listed once.
                        else -> NotesChangesPage(listOf(aChange("c2", "2026-10-10T09:00:00Z"), aChange("c1", "2026-10-08T08:00:00Z")), more = false)
                    }
                )
            },
        )
        NotesChangesPresenter(room, repository, dateFormatter).test {
            val first = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(first.hasMore).isTrue()
            first.eventSink(NotesChangesEvent.LoadMore)
            val state = consumeItemsUntilPredicate { !it.isLoadingMore && !it.hasMore }.last()
            assertThat(repository.changeHistoryRequests).containsExactly(null, "c2").inOrder()
            assertThat(state.days.flatMap { day -> day.entries.map { it.commit } }).containsExactly("c3", "c2", "c1").inOrder()
        }
    }

    /** Offline, the list says so; retrying loads it. */
    @Test
    fun `a failed first page can be retried`() = runTest {
        var fail = true
        val repository = FakeNotesRepository(
            changeHistoryResult = { _, _ ->
                if (fail) Result.failure(offline) else Result.success(NotesChangesPage(listOf(aChange("c1", "2026-10-10T18:00:00Z")), more = false))
            },
        )
        NotesChangesPresenter(room, repository, dateFormatter).test {
            val failed = consumeItemsUntilPredicate { !it.isLoading && it.failed }.last()
            assertThat(failed.days).isEmpty()
            fail = false
            failed.eventSink(NotesChangesEvent.Retry)
            val state = consumeItemsUntilPredicate { !it.isLoading && !it.failed && it.days.isNotEmpty() }.last()
            assertThat(state.days.single().entries.single().commit).isEqualTo("c1")
        }
    }

    /**
     * What tapping a file does: a note that's there opens; a deleted note (or one gone since) opens its history; a photo that's
     * there opens in another app; a deleted photo can't be opened.
     */
    @Test
    fun `files open according to what they are now`() {
        val files = listOf(
            aLocalFile("Súper.md", isNote = true),
            aLocalFile("Fotos/luna.jpg", isNote = false),
        ).associateBy { it.path }
        fun target(path: String, change: FileChange) = with(NotesChangesPresenter) { ChangedFile(path, change, null).toHistoryFile(files) }.target
        assertThat(target("Súper.md", FileChange.MODIFIED)).isEqualTo(HistoryFileTarget.NOTE)
        assertThat(target("Viejo.md", FileChange.DELETED)).isEqualTo(HistoryFileTarget.NOTE_HISTORY)
        assertThat(target("Movida.md", FileChange.MODIFIED)).isEqualTo(HistoryFileTarget.NOTE_HISTORY)
        assertThat(target("Fotos/luna.jpg", FileChange.ADDED)).isEqualTo(HistoryFileTarget.FILE)
        assertThat(target("Fotos/sol.jpg", FileChange.DELETED)).isEqualTo(HistoryFileTarget.NONE)
        val renamed = with(NotesChangesPresenter) { ChangedFile("Súper.md", FileChange.RENAMED, "Compras.md").toHistoryFile(files) }
        assertThat(renamed.name).isEqualTo("Súper")
        assertThat(renamed.fromName).isEqualTo("Compras")
    }

    /** Tapping a photo downloads it and hands it to another app; offline, it says so. */
    @Test
    fun `opening a photo downloads it`() = runTest {
        val repository = FakeNotesRepository().apply { attachmentResult = { Result.success(File("/cache/luna.jpg")) } }
        NotesChangesPresenter(room, repository, dateFormatter).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NotesChangesEvent.OpenFile("Fotos/luna.jpg", "luna.jpg"))
            val opening = consumeItemsUntilPredicate { it.openFile != null }.last()
            assertThat(opening.openFile?.name).isEqualTo("luna.jpg")
            opening.eventSink(NotesChangesEvent.FileOpenHandled(opened = false))
            assertThat(consumeItemsUntilPredicate { it.fileProblem != null }.last().fileProblem).isEqualTo(HistoryFileProblem.NO_APP)
        }
    }

    /** A note's versions are grouped by day with their labels; the version screen's title says when. */
    @Test
    fun `note history lists versions`() = runTest {
        val repository = FakeNotesRepository(
            historyResult = { _, path ->
                assertThat(path).isEqualTo("Recetas/Mole.md")
                Result.success(
                    listOf(
                        NoteVersion("v2", "2026-10-10T18:02:00Z", "add chiles", ChangeAuthor("Luis", "reaction", "", "Luis")),
                        NoteVersion("v1", "2026-10-01T08:00:00Z", "app: edit Mole.md", ChangeAuthor(null, null, "", "Ana")),
                    )
                )
            },
        )
        NoteHistoryPresenter(room, "Recetas/Mole.md", repository, dateFormatter).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.title).isEqualTo("Mole")
            assertThat(state.days.map { it.label }).containsExactly("2026-10-10", "2026-10-01").inOrder()
            assertThat(state.days[0].entries.single().label).isEqualTo(ChangeLabel.Text(R.string.screen_notes_change_reaction, listOf("Luis")))
            assertThat(state.days[1].entries.single().label).isEqualTo(ChangeLabel.Plain("Ana"))
            assertThat(state.whenOf("v2")).isEqualTo("2026-10-10, 18:02")
        }
    }

    /** A version is read with its commit and shown read-only; offline, it can be retried. */
    @Test
    fun `a version is read at its commit`() = runTest {
        var fail = true
        val repository = FakeNotesRepository(
            noteAtResult = { _, path, commit ->
                if (fail) Result.failure(offline) else Result.success(Note(path, "blob-$commit", "# Mole\n- chiles"))
            },
        )
        NoteVersionPresenter(NoteVersionPresenter.Version(room, "Recetas/Mole.md", "v2", "2026-10-10, 18:02"), repository).test {
            val failed = consumeItemsUntilPredicate { !it.isLoading && it.failed }.last()
            assertThat(failed.content).isNull()
            assertThat(failed.savedAt).isEqualTo("2026-10-10, 18:02")
            fail = false
            failed.eventSink(NoteVersionEvent.Retry)
            val state = consumeItemsUntilPredicate { !it.isLoading && it.content != null }.last()
            assertThat(state.content).isEqualTo("# Mole\n- chiles")
            assertThat(state.title).isEqualTo("Mole")
        }
    }

    private fun aChange(commit: String, at: String, how: String? = "app", who: String? = "Ana", detail: String = "", by: String = "Ana") = NotesChange(
        commit = commit,
        at = at,
        message = "message $commit",
        author = ChangeAuthor(who, how, detail, by),
        files = listOf(ChangedFile("Súper.md", FileChange.MODIFIED, null)),
    )

    private fun aLocalFile(path: String, isNote: Boolean) = LocalFile(
        path = path,
        version = "v1",
        size = 10,
        modified = 0,
        isNote = isNote,
        content = if (isNote) "text" else null,
        hasLocalEdits = false,
    )
}
