/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import app.cash.turbine.ReceiveTurbine
import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.MovedNote
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.api.ServerCopy
import co.artise.android.notes.impl.ui.choices.NotesChoicesEvent
import co.artise.android.notes.impl.ui.choices.NotesChoicesPresenter
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.editor.FormatAction
import co.artise.android.notes.impl.ui.editor.NoteEditorEvent
import co.artise.android.notes.impl.ui.editor.NoteEditorPresenter
import co.artise.android.notes.impl.ui.folder.NewNoteDialog
import co.artise.android.notes.impl.ui.folder.NotesFolderEvent
import co.artise.android.notes.impl.ui.folder.NotesFolderPresenter
import co.artise.android.notes.impl.ui.note.NoteDialog
import co.artise.android.notes.impl.ui.note.NoteEvent
import co.artise.android.notes.impl.ui.note.NoteNavigator
import co.artise.android.notes.impl.ui.note.NotePresenter
import co.artise.android.notes.impl.ui.note.RenameFailure
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Records where a note screen sent the person. */
class RecordingNoteNavigator : NoteNavigator {
    val opened = mutableListOf<String>()
    val edited = mutableListOf<String>()
    val renamedTo = mutableListOf<String>()
    var deleted = false

    override fun openNote(path: String) {
        opened += path
    }

    override fun openEditor(path: String) {
        edited += path
    }

    override fun onRenamed(newPath: String) {
        renamedTo += newPath
    }

    override fun onDeleted() {
        deleted = true
    }
}

class NotesEditingPresentersTest {
    private val room = RoomId("!familia:artise.co")

    // Editor

    /** Saving stores the text on the phone, starts sending it, and closes the editor. */
    @Test
    fun `editor saves and sends in the background`() = runTest {
        var done = false
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- leche"))))
        NoteEditorPresenter(room, "Súper.md", null, { done = true }, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.text.text.toString()).isEqualTo("- leche")
            state.text.setTextAndPlaceCursorAtEnd("- leche\n- pan")
            consumeItemsUntilPredicate { it.hasUnsavedChanges }.last().eventSink(NoteEditorEvent.Save)
            awaitUntil { done }
            assertThat(repository.savedEdits).containsExactly("Súper.md" to "- leche\n- pan")
            assertThat(repository.backgroundSyncs).containsExactly(room)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Leaving with unsaved text asks first; discarding leaves without saving. */
    @Test
    fun `editor asks before discarding changes`() = runTest {
        var done = false
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- leche"))))
        NoteEditorPresenter(room, "Súper.md", null, { done = true }, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.text.setTextAndPlaceCursorAtEnd("- leche\n- pan")
            consumeItemsUntilPredicate { it.hasUnsavedChanges }.last().eventSink(NoteEditorEvent.Back)
            val asking = consumeItemsUntilPredicate { it.showSaveChangesDialog }.last()
            assertThat(done).isFalse()
            asking.eventSink(NoteEditorEvent.DiscardChanges)
            assertThat(done).isTrue()
            assertThat(repository.savedEdits).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Typing "[[mo" suggests Mole; choosing it completes the link. */
    @Test
    fun `editor suggests and completes links`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", ""), aNote("Recetas/Mole.md"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.text.setTextAndPlaceCursorAtEnd("Para el [[mo")
            val suggesting = consumeItemsUntilPredicate { it.suggestions.isNotEmpty() }.last()
            assertThat(suggesting.suggestions.single().path).isEqualTo("Recetas/Mole.md")
            suggesting.eventSink(NoteEditorEvent.SelectSuggestion(suggesting.suggestions.single()))
            val completed = consumeItemsUntilPredicate { it.suggestions.isEmpty() }.last()
            assertThat(completed.text.text.toString()).isEqualTo("Para el [[Mole]]")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A toolbar button formats the selection in the editor's text, keeping it selected. */
    @Test
    fun `toolbar formats the selection`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "comprar pan"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.text.edit { selection = TextRange(8, 11) }
            state.eventSink(NoteEditorEvent.Format(FormatAction.BOLD))
            val formatted = consumeItemsUntilPredicate { it.hasUnsavedChanges }.last()
            assertThat(formatted.text.text.toString()).isEqualTo("comprar **pan**")
            assertThat(formatted.text.selection).isEqualTo(TextRange(10, 13))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Combining starts with this phone's text, a line, then the other version; saving resolves the conflict with it. */
    @Test
    fun `editor combines a conflict`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"))))
        repository.edits[room] = listOf(aConflict(id = 7))
        NoteEditorPresenter(room, "Súper.md", 7, {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.isResolvingConflict).isTrue()
            assertThat(state.text.text.toString()).isEqualTo("- huevos" + NoteEditorPresenter.CONFLICT_SEPARATOR + "- pan")
            // Editing a combined text changes nothing else on screen (it's already unsaved), so save from the same state.
            state.text.setTextAndPlaceCursorAtEnd("- huevos\n- pan")
            state.eventSink(NoteEditorEvent.Save)
            awaitUntil { repository.resolved.isNotEmpty() }
            assertThat(repository.resolved).containsExactly(7L to "- huevos\n- pan")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Choices

    /** Each choice calls the matching repository action and sends it; "keep both" picks a free name. */
    @Test
    fun `choices apply the person's decision`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"), aNote("Ideas.md"))))
        repository.edits[room] = listOf(
            aConflict(id = 1),
            aConflict(id = 2).copy(state = EditState.EXISTS, path = "Ideas.md"),
            aConflict(id = 3).copy(state = EditState.DELETED, serverCopy = null),
            aConflict(id = 4).copy(state = EditState.PENDING),
        )
        NotesChoicesPresenter(room, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.items.map { it.editId }).containsExactly(1L, 2L, 3L).inOrder()
            state.eventSink(NotesChoicesEvent.KeepMine(1))
            state.eventSink(NotesChoicesEvent.KeepBoth(2))
            state.eventSink(NotesChoicesEvent.KeepMyCopy(3))
            awaitUntil { repository.resolved.size == 3 }
            assertThat(repository.resolved).containsExactly(1L to "- huevos", 2L to "Ideas (2).md", 3L to "keep")
            assertThat(repository.backgroundSyncs).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Folder: new note

    /** A new note is created in the folder with its name as a title, then opened in the editor. */
    @Test
    fun `folder creates a note and opens it`() = runTest {
        val opened = mutableListOf<String>()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Recetas/Mole.md"))), seenPrivacyNotice = true)
        NotesFolderPresenter(room, "Recetas", { opened += it }, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            state.eventSink(NotesFolderEvent.StartNewNote)
            consumeItemsUntilPredicate { it.newNote != null }.last().eventSink(NotesFolderEvent.CreateNote(" Flan "))
            consumeItemsUntilPredicate { it.newNote == null }
            assertThat(repository.createdNotes).containsExactly("Recetas/Flan.md" to "# Flan\n\n")
            assertThat(opened).containsExactly("Recetas/Flan.md")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A taken or invalid name keeps the dialog open and says why; nothing is created. */
    @Test
    fun `folder refuses bad names`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Recetas/Mole.md"))), seenPrivacyNotice = true)
        NotesFolderPresenter(room, "Recetas", {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            state.eventSink(NotesFolderEvent.StartNewNote)
            state.eventSink(NotesFolderEvent.CreateNote("mole"))
            assertThat(consumeItemsUntilPredicate { it.newNote?.problem != null }.last().newNote).isEqualTo(NewNoteDialog(NoteNameProblem.EXISTS))
            state.eventSink(NotesFolderEvent.CreateNote("a/b"))
            assertThat(consumeItemsUntilPredicate { it.newNote?.problem == NoteNameProblem.INVALID }.last().newNote?.problem)
                .isEqualTo(NoteNameProblem.INVALID)
            assertThat(repository.createdNotes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Note: create from link, rename, delete

    /** A link to a missing note can create it next to the current note, then open it to write. */
    @Test
    fun `missing link creates the note`() = runTest {
        val navigator = RecordingNoteNavigator()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Recetas/Mole.md"))))
        NotePresenter(room, "Recetas/Mole.md", navigator, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.OpenNoteLink("Salsa"))
            consumeItemsUntilPredicate { it.dialog is NoteDialog.MissingNote }.last().eventSink(NoteEvent.CreateMissingNote)
            awaitUntil { navigator.edited.isNotEmpty() }
            assertThat(repository.createdNotes.single().first).isEqualTo("Recetas/Salsa.md")
            assertThat(navigator.edited).containsExactly("Recetas/Salsa.md")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Renaming moves the note on the server and shows it under its new name. */
    @Test
    fun `rename moves the note`() = runTest {
        val navigator = RecordingNoteNavigator()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"))))
        repository.moveResult = { from, to -> Result.success(MovedNote(from, to, "v2", emptyList())) }
        NotePresenter(room, "Súper.md", navigator, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.StartRename)
            consumeItemsUntilPredicate { it.dialog is NoteDialog.Rename }.last().eventSink(NoteEvent.Rename("Compras"))
            awaitUntil { navigator.renamedTo.isNotEmpty() }
            assertThat(navigator.renamedTo).containsExactly("Compras.md")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Offline, or with unsent changes, renaming explains what to do instead of failing silently. */
    @Test
    fun `rename failures are explained`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"))))
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.Rename("Compras"))
            assertThat(consumeItemsUntilPredicate { it.dialog is NoteDialog.RenameFailed }.last().dialog)
                .isEqualTo(NoteDialog.RenameFailed(RenameFailure.OFFLINE))
            repository.moveResult = { _, _ -> Result.failure(NotesException.Conflict("unsent", null)) }
            state.eventSink(NoteEvent.Rename("Lista"))
            assertThat(consumeItemsUntilPredicate { it.dialog == NoteDialog.RenameFailed(RenameFailure.UNSENT_CHANGES) }.last().dialog)
                .isEqualTo(NoteDialog.RenameFailed(RenameFailure.UNSENT_CHANGES))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Deleting asks first, then removes the note, sends it, and leaves the screen. */
    @Test
    fun `delete after confirming`() = runTest {
        val navigator = RecordingNoteNavigator()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"))))
        NotePresenter(room, "Súper.md", navigator, repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.StartDelete)
            consumeItemsUntilPredicate { it.dialog == NoteDialog.ConfirmDelete }.last().eventSink(NoteEvent.ConfirmDelete)
            awaitUntil { navigator.deleted }
            assertThat(repository.deletedFiles).containsExactly("Súper.md")
            assertThat(repository.backgroundSyncs).containsExactly(room)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun aConflict(id: Long) = PendingEdit(
        id = id,
        path = "Súper.md",
        kind = EditKind.SAVE,
        state = EditState.CONFLICT,
        content = "- huevos",
        serverCopy = ServerCopy("Súper.md", "v2", "- pan"),
        error = null,
    )
}

/** Lets launched work finish: consumes states until [done] holds, for up to about a second. */
private suspend fun <T : Any> ReceiveTurbine<T>.awaitUntil(done: () -> Boolean) {
    repeat(50) {
        if (done()) return
        consumeItemsUntilTimeout(20.milliseconds)
    }
}
