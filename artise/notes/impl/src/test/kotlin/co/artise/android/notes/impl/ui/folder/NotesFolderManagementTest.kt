/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.DeletedFolder
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.impl.A_ROOM
import co.artise.android.notes.impl.ui.FakeNotesRepository
import co.artise.android.notes.impl.ui.common.NoteNameProblem
import co.artise.android.notes.impl.ui.common.NoteNames
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotesFolderManagementTest {
    private val room = RoomId(A_ROOM)

    private fun note(path: String) = LocalFile(path, "v", 10, 0, isNote = true, content = "x", hasLocalEdits = false)

    private fun file(path: String) = LocalFile(path, "v", 1000, 0, isNote = false, content = null, hasLocalEdits = false)

    private fun repository(vararg files: LocalFile, folders: List<String> = emptyList()) =
        FakeNotesRepository(files = mutableMapOf(room to files.toList())).apply { this.folders[room] = folders }

    /** Empty folders from the server show in the list, with no notes or files counted. */
    @Test
    fun `empty folders show`() {
        val entries = NotesFolderEntries.of(listOf(note("Mole.md")), "", listOf("Viajes", "Viajes/Oaxaca", "Recetas"))
        assertThat(entries).containsExactly(
            NotesFolderEntry.Folder("Recetas", "Recetas", 0, 0),
            NotesFolderEntry.Folder("Viajes", "Viajes", 0, 0),
            NotesFolderEntry.Note("Mole", "Mole.md", hasLocalEdits = false),
        ).inOrder()
        assertThat(NotesFolderEntries.of(emptyList(), "Viajes", listOf("Viajes", "Viajes/Oaxaca")))
            .containsExactly(NotesFolderEntry.Folder("Oaxaca", "Viajes/Oaxaca", 0, 0))
    }

    /** A folder's new name: no "/", and not the name of another folder or file next to it. */
    @Test
    fun `folder naming rules`() {
        val taken = listOf("Recetas", "Viajes", "Mole.md", "Recetas/Postres")
        assertThat(NoteNames.folderRenameProblem("Recetas", "viajes", taken)).isEqualTo(NoteNameProblem.EXISTS)
        assertThat(NoteNames.folderRenameProblem("Recetas", "Cocina", taken)).isNull()
        assertThat(NoteNames.folderRenameProblem("Recetas", "RECETAS", taken)).isNull()
        assertThat(NoteNames.folderRenameProblem("Recetas/Postres", "Viajes", taken)).isNull()
        assertThat(NoteNames.folderRenameProblem("Recetas", "a/b", taken)).isEqualTo(NoteNameProblem.INVALID)
    }

    /** "+", New folder: it's created inside the open folder; a name already taken is explained instead. */
    @Test
    fun `create a folder`() = runTest {
        val repository = repository(note("Viajes/Oaxaca.md"), folders = listOf("Viajes", "Viajes/Fotos"))
        NotesFolderPresenter(room, "Viajes", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowNewMenu)
            consumeItemsUntilPredicate { it.showNewMenu }.last().eventSink(NotesFolderEvent.StartCreateFolder)
            val dialog = consumeItemsUntilPredicate { it.dialog is FolderDialog.CreateFolder }.last()
            assertThat(dialog.showNewMenu).isFalse()
            dialog.eventSink(NotesFolderEvent.CreateFolder("fotos"))
            val taken = consumeItemsUntilPredicate { (it.dialog as? FolderDialog.CreateFolder)?.problem != null }.last()
            assertThat(taken.dialog).isEqualTo(FolderDialog.CreateFolder(NoteNameProblem.EXISTS))
            taken.eventSink(NotesFolderEvent.CreateFolder(" Chiapas "))
            consumeItemsUntilPredicate { repository.createdFolders.isNotEmpty() && it.dialog == null && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.createdFolders).containsExactly("Viajes/Chiapas")
    }

    /** Renaming a folder moves it, keeping its parent. */
    @Test
    fun `rename a folder`() = runTest {
        val repository = repository(note("Viajes/Oaxaca/Día 1.md"))
        NotesFolderPresenter(room, "Viajes", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartRename)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.Rename }.last().eventSink(NotesFolderEvent.Rename("Oaxaca 2026"))
            consumeItemsUntilPredicate { repository.movedFolders.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.movedFolders).containsExactly("Viajes/Oaxaca" to "Viajes/Oaxaca 2026")
    }

    /** Move to…: a folder can't be moved into itself or its own subfolders. */
    @Test
    fun `move a folder`() = runTest {
        val repository = repository(note("Recetas/Mole.md"), folders = listOf("Recetas/Postres", "Cocina"))
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.size == 2 }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.first { it.name == "Recetas" }))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartMove)
            val picker = consumeItemsUntilPredicate { it.dialog is FolderDialog.MoveTo }.last()
            assertThat((picker.dialog as FolderDialog.MoveTo).folders).containsExactly("Cocina")
            picker.eventSink(NotesFolderEvent.MoveTo("Cocina"))
            consumeItemsUntilPredicate { repository.movedFolders.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.movedFolders).containsExactly("Recetas" to "Cocina/Recetas")
    }

    /** Delete asks first, saying what's inside, then deletes everything in it. */
    @Test
    fun `delete a folder with what's inside`() = runTest {
        val repository = repository(note("Viajes/Oaxaca.md"), note("Viajes/Fotos/Día 1.md"), file("Viajes/mapa.pdf"), folders = listOf("Viajes/Vacía"))
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartDelete)
            val confirm = consumeItemsUntilPredicate { it.dialog is FolderDialog.ConfirmDeleteFolder }.last()
            val dialog = confirm.dialog as FolderDialog.ConfirmDeleteFolder
            assertThat(listOf(dialog.notes, dialog.files, dialog.folders)).containsExactly(2, 1, 2).inOrder()
            confirm.eventSink(NotesFolderEvent.ConfirmDelete)
            consumeItemsUntilPredicate { repository.deletedFolders.isNotEmpty() && it.entries.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.deletedFolders).containsExactly("Viajes" to true)
    }

    /** The server holds more than the phone knew of: it refuses, and the confirmation comes back with its counts. */
    @Test
    fun `server refuses a folder that isn't empty`() = runTest {
        val repository = repository(folders = listOf("Viajes")).apply {
            deleteFolderResult = { _, recursive ->
                if (recursive) Result.success(DeletedFolder("Viajes", 0, 4)) else Result.failure(NotesException.NotEmpty("not_empty", files = 4, folders = 1))
            }
        }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartDelete)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.ConfirmDeleteFolder }.last().eventSink(NotesFolderEvent.ConfirmDelete)
            val again = consumeItemsUntilPredicate { (it.dialog as? FolderDialog.ConfirmDeleteFolder)?.files == 4 }.last()
            assertThat((again.dialog as FolderDialog.ConfirmDeleteFolder).folders).isEqualTo(1)
            assertThat(repository.deletedFolders).isEmpty()
            again.eventSink(NotesFolderEvent.ConfirmDelete)
            consumeItemsUntilPredicate { repository.deletedFolders.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.deletedFolders).containsExactly("Viajes" to true)
    }

    /** An empty folder offers to delete itself; once deleted, the screen leaves. The top level never does. */
    @Test
    fun `delete this empty folder`() = runTest {
        val repository = repository(folders = listOf("Viajes"))
        NotesFolderPresenter(room, "Viajes", {}, repository).test {
            val empty = consumeItemsUntilPredicate { !it.isRefreshing && it.canDeleteFolder }.last()
            empty.eventSink(NotesFolderEvent.DeleteThisFolder)
            consumeItemsUntilPredicate { it.isGone }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.deletedFolders).containsExactly("Viajes" to false)
        NotesFolderPresenter(room, "", {}, FakeNotesRepository(seenPrivacyNotice = true)).test {
            assertThat(consumeItemsUntilPredicate { !it.isRefreshing }.last().canDeleteFolder).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
