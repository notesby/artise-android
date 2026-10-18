/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.MediaInUseException
import co.artise.android.notes.api.MovedNote
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

class NotesFileActionsTest {
    private val room = RoomId(A_ROOM)

    private fun note(path: String) = LocalFile(path, "v", 10, 0, isNote = true, content = "x", hasLocalEdits = false)

    private fun file(path: String) = LocalFile(path, "v", 1000, 0, isNote = false, content = null, hasLocalEdits = false)

    private fun repository(vararg files: LocalFile) = FakeNotesRepository(files = mutableMapOf(room to files.toList())).apply {
        moveResult = { _, to -> Result.success(MovedNote("from", to, "v2", emptyList())) }
    }

    /** Names: a renamed file keeps its extension; a move keeps the name; folder names are checked. */
    @Test
    fun `naming rules`() {
        assertThat(NoteNames.renamedPath("Recetas/Mole.md", "Mole poblano")).isEqualTo("Recetas/Mole poblano.md")
        assertThat(NoteNames.renamedPath("attachments/factura.pdf", "recibo")).isEqualTo("attachments/recibo.pdf")
        assertThat(NoteNames.renamedPath("factura.pdf", "recibo.PDF")).isEqualTo("recibo.PDF")
        assertThat(NoteNames.renameProblem("a.md", "b", listOf("a.md", "B.md"))).isEqualTo(NoteNameProblem.EXISTS)
        assertThat(NoteNames.renameProblem("a.md", "A", listOf("a.md"))).isNull()
        assertThat(NoteNames.renameProblem("a.md", "x/y", listOf("a.md"))).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.movedPath("Recetas/Mole.md", "")).isEqualTo("Mole.md")
        assertThat(NoteNames.movedPath("Mole.md", "Cocina/Recetas")).isEqualTo("Cocina/Recetas/Mole.md")
        assertThat(NoteNames.folderProblem("Recetas/Postres")).isNull()
        assertThat(NoteNames.folderProblem("Recetas//x")).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.folderProblem(".oculta")).isEqualTo(NoteNameProblem.INVALID)
        assertThat(NoteNames.folderPath(" Recetas / Postres/ ")).isEqualTo("Recetas/Postres")
    }

    /** Folders list photos and files too, and count them in their subfolders. */
    @Test
    fun `folders show files`() {
        val entries = NotesFolderEntries.of(listOf(note("Mole.md"), file("luna.jpg"), file("Viajes/mapa.pdf"), note("Viajes/Oaxaca.md")), "")
        assertThat(entries).containsExactly(
            NotesFolderEntry.Folder("Viajes", "Viajes", 1, 1),
            NotesFolderEntry.Note("Mole", "Mole.md", hasLocalEdits = false),
            NotesFolderEntry.File("luna.jpg", "luna.jpg", isImage = true, size = 1000),
        ).inOrder()
        assertThat(NotesFolderEntries.allFolders(listOf(note("A/B/c.md"), file("D/e.jpg")))).containsExactly("A", "A/B", "D").inOrder()
    }

    /** Long press, Move to…, a folder: the note moves there through the server. */
    @Test
    fun `move a note to another folder`() = runTest {
        val moves = mutableListOf<Pair<String, String>>()
        val repository = repository(note("Súper.md"), note("Recetas/Mole.md")).apply {
            moveResult = { from, to ->
                moves += from to to
                Result.success(MovedNote("from", to, "v2", emptyList()))
            }
        }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.first { it.name == "Súper" }))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartMove)
            val picker = consumeItemsUntilPredicate { it.dialog is FolderDialog.MoveTo }.last()
            assertThat((picker.dialog as FolderDialog.MoveTo).folders).containsExactly("Recetas")
            picker.eventSink(NotesFolderEvent.MoveTo("Recetas"))
            consumeItemsUntilPredicate { moves.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(moves).containsExactly("Súper.md" to "Recetas/Súper.md")
    }

    /** A new folder is made by moving into it; a bad folder name is explained and nothing moves. */
    @Test
    fun `move into a new folder`() = runTest {
        val moves = mutableListOf<String>()
        val repository = repository(note("Súper.md")).apply {
            moveResult = { _, to ->
                moves += to
                Result.success(MovedNote("from", to, "v2", emptyList()))
            }
        }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartMove)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.MoveTo }.last().eventSink(NotesFolderEvent.StartNewFolder)
            val naming = consumeItemsUntilPredicate { it.dialog is FolderDialog.NewFolder }.last()
            naming.eventSink(NotesFolderEvent.MoveToNewFolder(".x"))
            val bad = consumeItemsUntilPredicate { (it.dialog as? FolderDialog.NewFolder)?.problem != null }.last()
            bad.eventSink(NotesFolderEvent.MoveToNewFolder("Listas/Casa"))
            consumeItemsUntilPredicate { moves.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(moves).containsExactly("Listas/Casa/Súper.md")
    }

    /** Renaming a file keeps its extension; a taken name is explained under the field. */
    @Test
    fun `rename a file`() = runTest {
        val moves = mutableListOf<String>()
        val repository = repository(file("factura.pdf"), file("recibo.pdf")).apply {
            moveResult = { _, to ->
                moves += to
                Result.success(MovedNote("from", to, "v2", emptyList()))
            }
        }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.first { it.name == "factura.pdf" }))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartRename)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.Rename }.last().eventSink(NotesFolderEvent.Rename("recibo"))
            val taken = consumeItemsUntilPredicate { (it.dialog as? FolderDialog.Rename)?.problem == NoteNameProblem.EXISTS }.last()
            taken.eventSink(NotesFolderEvent.Rename("factura 2026"))
            consumeItemsUntilPredicate { moves.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(moves).containsExactly("factura 2026.pdf")
    }

    /** Offline or with unsent changes, a move says why instead of failing silently. */
    @Test
    fun `move problems are explained`() = runTest {
        val repository = repository(note("Súper.md")).apply { moveResult = { _, _ -> Result.failure(NotesException.Conflict("unsent", null)) } }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartMove)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.MoveTo }.last().eventSink(NotesFolderEvent.MoveTo("Otra"))
            assertThat(consumeItemsUntilPredicate { it.dialog is FolderDialog.Problem }.last().dialog).isEqualTo(FolderDialog.Problem(FileProblem.UNSENT))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Deleting a file that notes use asks first, then takes it out of them. */
    @Test
    fun `delete a file used in notes`() = runTest {
        val repository = repository(file("luna.jpg")).apply {
            deleteMediaResult = { _, removeFromNotes -> if (removeFromNotes) Result.success(Unit) else Result.failure(MediaInUseException(listOf("Mole.md"))) }
        }
        NotesFolderPresenter(room, "", {}, repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isRefreshing && it.entries.isNotEmpty() }.last()
            loaded.eventSink(NotesFolderEvent.ShowActions(loaded.entries.single()))
            consumeItemsUntilPredicate { it.actionsFor != null }.last().eventSink(NotesFolderEvent.StartDelete)
            consumeItemsUntilPredicate { it.dialog is FolderDialog.ConfirmDelete }.last().eventSink(NotesFolderEvent.ConfirmDelete)
            val inUse = consumeItemsUntilPredicate { it.dialog is FolderDialog.FileInUse }.last()
            assertThat((inUse.dialog as FolderDialog.FileInUse).usedBy).containsExactly("Mole.md")
            inUse.eventSink(NotesFolderEvent.ConfirmRemoveAndDelete)
            consumeItemsUntilPredicate { repository.deletedMedia.isNotEmpty() && it.busyPath == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.deletedMedia).containsExactly("luna.jpg" to true)
    }
}
