/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import co.artise.android.notes.impl.ui.editor.AttachError
import co.artise.android.notes.impl.ui.editor.AttachmentReader
import co.artise.android.notes.impl.ui.editor.AttachmentTooBigException
import co.artise.android.notes.impl.ui.editor.EditableLink
import co.artise.android.notes.impl.ui.editor.FormatAction
import co.artise.android.notes.impl.ui.editor.NoteEditorEvent
import co.artise.android.notes.impl.ui.editor.NoteEditorPresenter
import co.artise.android.notes.impl.ui.editor.PickedFile
import co.artise.android.notes.impl.ui.folder.NewNoteDialog
import co.artise.android.notes.impl.ui.folder.NotesFolderEvent
import co.artise.android.notes.impl.ui.folder.NotesFolderPresenter
import co.artise.android.notes.impl.ui.note.NoteDialog
import co.artise.android.notes.impl.ui.note.NoteEvent
import co.artise.android.notes.impl.ui.note.NoteNavigator
import co.artise.android.notes.impl.ui.note.NotePresenter
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import co.artise.android.notes.impl.ui.note.RenameFailure
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
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
    private val clock = FakeSystemClock(epochMillisResult = 1_000_000)
    private var reader = AttachmentReader { Result.success(PickedFile("luna.jpg", "image/jpeg", byteArrayOf(1))) }

    // Editor

    /** Saving stores the text on the phone, starts sending it, and closes the editor. */
    @Test
    fun `editor saves and sends in the background`() = runTest {
        var done = false
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- leche"))))
        NoteEditorPresenter(room, "Súper.md", null, { done = true }, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.value.text).isEqualTo("- leche")
            state.eventSink(NoteEditorEvent.ValueChanged(typed("- leche\n- pan")))
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
        NoteEditorPresenter(room, "Súper.md", null, { done = true }, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(typed("- leche\n- pan")))
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
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(typed("Para el [[mo")))
            val suggesting = consumeItemsUntilPredicate { it.suggestions.isNotEmpty() }.last()
            assertThat(suggesting.suggestions.single().path).isEqualTo("Recetas/Mole.md")
            suggesting.eventSink(NoteEditorEvent.SelectSuggestion(suggesting.suggestions.single()))
            val completed = consumeItemsUntilPredicate { it.suggestions.isEmpty() }.last()
            assertThat(completed.value.text).isEqualTo("Para el [[Mole]]")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Enter on a list line starts the next item. */
    @Test
    fun `enter continues a list`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- leche"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(typed("- leche\n")))
            assertThat(consumeItemsUntilPredicate { it.value.text != "- leche" }.last().value).isEqualTo(typed("- leche\n- "))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Tapping the checkbox of a formatted line ticks it and leaves the cursor where it was. */
    @Test
    fun `tapping a checkbox on a formatted line ticks it`() = runTest {
        val text = "Lista\n- [ ] leche"
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", text))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(state.value.copy(selection = TextRange(2))))
            val onFirstLine = consumeItemsUntilPredicate { it.value.selection == TextRange(2) }.last()
            // A tap on the box lands inside "[ ]" of line 1, which was showing formatted.
            onFirstLine.eventSink(NoteEditorEvent.ValueChanged(onFirstLine.value.copy(selection = TextRange(text.indexOf("[ ]") + 1))))
            val ticked = consumeItemsUntilPredicate { it.value.text.contains("[x]") }.last()
            assertThat(ticked.value.text).isEqualTo("Lista\n- [x] leche")
            assertThat(ticked.value.selection).isEqualTo(TextRange(2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Tapping the text next to a checkbox just moves the cursor there, showing that line's Markdown. */
    @Test
    fun `tapping the text next to a checkbox places the cursor`() = runTest {
        val text = "Lista\n- [ ] leche"
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", text))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(state.value.copy(selection = TextRange(2))))
            val onFirstLine = consumeItemsUntilPredicate { it.value.selection == TextRange(2) }.last()
            onFirstLine.eventSink(NoteEditorEvent.ValueChanged(onFirstLine.value.copy(selection = TextRange(text.indexOf("leche") + 2))))
            val moved = consumeItemsUntilPredicate { it.value.selection.start > 5 }.last()
            assertThat(moved.value.text).isEqualTo(text)
            assertThat(moved.rawLines).isEqualTo(1..1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Tapping a link on a formatted line opens the link dialog; saving writes the new link, removing keeps the text. */
    @Test
    fun `tapping a link edits it`() = runTest {
        val text = "Ver [[Mole]] hoy\nfin"
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", text))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            // The cursor starts at the end, on "fin"; the first line shows formatted.
            state.eventSink(NoteEditorEvent.ValueChanged(state.value.copy(selection = TextRange(text.indexOf("Mole") + 1))))
            val editing = consumeItemsUntilPredicate { it.linkEdit != null }.last()
            assertThat(editing.linkEdit?.link).isEqualTo(EditableLink(isNote = true, target = "Mole", shownText = ""))
            editing.eventSink(NoteEditorEvent.SaveLink(EditableLink(isNote = true, target = "Recetas/Mole", shownText = "el mole")))
            val saved = consumeItemsUntilPredicate { it.linkEdit == null }.last()
            assertThat(saved.value.text).isEqualTo("Ver [[Recetas/Mole|el mole]] hoy\nfin")

            saved.eventSink(NoteEditorEvent.ValueChanged(saved.value.copy(selection = TextRange(saved.value.text.indexOf("mole") + 1))))
            consumeItemsUntilPredicate { it.linkEdit != null }.last().eventSink(NoteEditorEvent.RemoveLink)
            assertThat(consumeItemsUntilPredicate { it.linkEdit == null }.last().value.text).isEqualTo("Ver el mole hoy\nfin")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Typing without a pause is one undo step, formatting is its own step, and redo brings changes back.
     * A new change after undoing clears what could be redone.
     */
    @Test
    fun `undo and redo`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", ""))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(typed("l")))
            clock.epochMillisResult += 200
            state.eventSink(NoteEditorEvent.ValueChanged(typed("leche")))
            clock.epochMillisResult += 5_000
            state.eventSink(NoteEditorEvent.ValueChanged(typed("leche y pan")))
            state.eventSink(NoteEditorEvent.Format(FormatAction.BULLET_LIST))
            val formatted = consumeItemsUntilPredicate { it.value.text == "- leche y pan" }.last()
            assertThat(formatted.canUndo).isTrue()
            assertThat(formatted.canRedo).isFalse()

            formatted.eventSink(NoteEditorEvent.Undo)
            assertThat(consumeItemsUntilPredicate { it.value.text == "leche y pan" }.last().canRedo).isTrue()
            formatted.eventSink(NoteEditorEvent.Undo)
            consumeItemsUntilPredicate { it.value.text == "leche" }
            formatted.eventSink(NoteEditorEvent.Undo)
            assertThat(consumeItemsUntilPredicate { it.value.text == "" }.last().canUndo).isFalse()

            formatted.eventSink(NoteEditorEvent.Redo)
            consumeItemsUntilPredicate { it.value.text == "leche" }
            formatted.eventSink(NoteEditorEvent.ValueChanged(typed("leche!")))
            assertThat(consumeItemsUntilPredicate { it.value.text == "leche!" }.last().canRedo).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The web link button asks for an address; the selected text becomes the link's text. */
    @Test
    fun `web link from the toolbar`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "ver la tienda"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(state.value.copy(selection = TextRange(7, 13))))
            consumeItemsUntilPredicate { it.value.selection == TextRange(7, 13) }.last().eventSink(NoteEditorEvent.Format(FormatAction.WEB_LINK))
            val asking = consumeItemsUntilPredicate { it.linkEdit != null }.last()
            assertThat(asking.linkEdit?.isNew).isTrue()
            assertThat(asking.linkEdit?.link?.shownText).isEqualTo("tienda")
            asking.eventSink(NoteEditorEvent.SaveLink(EditableLink(isNote = false, target = "https://tienda.mx", shownText = "tienda")))
            val saved = consumeItemsUntilPredicate { it.linkEdit == null }.last()
            assertThat(saved.value.text).isEqualTo("ver la [tienda](https://tienda.mx)")
            assertThat(saved.value.selection).isEqualTo(TextRange(saved.value.text.length))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A photo is uploaded and embedded on a line of its own at the cursor; a file becomes a link. */
    @Test
    fun `attachments are uploaded and embedded`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "Hoy"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.Attach("content://photo"))
            val withPhoto = consumeItemsUntilPredicate { it.value.text.contains("luna") }.last()
            assertThat(withPhoto.value.text).isEqualTo("Hoy\n![[luna.jpg]]\n")
            assertThat(repository.attachments.single().first).isEqualTo("attachments/luna.jpg")
            reader = AttachmentReader { Result.success(PickedFile("factura.pdf", "application/pdf", byteArrayOf(2))) }
            cancelAndIgnoreRemainingEvents()
        }
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.Attach("content://file"))
            assertThat(consumeItemsUntilPredicate { it.value.text.contains("factura") }.last().value.text).isEqualTo("Hoy[[factura.pdf]]")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Offline or too big, attaching explains why instead of failing silently; the note is unchanged. */
    @Test
    fun `attachment problems are explained`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "Hoy"))))
        repository.addAttachmentResult = { Result.failure(NotesException.Network(IllegalStateException())) }
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.Attach("content://photo"))
            val offline = consumeItemsUntilPredicate { it.attachError != null }.last()
            assertThat(offline.attachError).isEqualTo(AttachError.OFFLINE)
            assertThat(offline.value.text).isEqualTo("Hoy")
            offline.eventSink(NoteEditorEvent.DismissAttachError)
            consumeItemsUntilPredicate { it.attachError == null }
            reader = AttachmentReader { Result.failure(AttachmentTooBigException()) }
            cancelAndIgnoreRemainingEvents()
        }
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.Attach("content://video"))
            assertThat(consumeItemsUntilPredicate { it.attachError != null }.last().attachError).isEqualTo(AttachError.TOO_BIG)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A toolbar button formats the selection in the editor's text, keeping it selected. */
    @Test
    fun `toolbar formats the selection`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "comprar pan"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEditorEvent.ValueChanged(state.value.copy(selection = TextRange(8, 11))))
            state.eventSink(NoteEditorEvent.Format(FormatAction.BOLD))
            val formatted = consumeItemsUntilPredicate { it.hasUnsavedChanges }.last()
            assertThat(formatted.value.text).isEqualTo("comprar **pan**")
            assertThat(formatted.value.selection).isEqualTo(TextRange(10, 13))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Combining starts with this phone's text, a line, then the other version; saving resolves the conflict with it. */
    @Test
    fun `editor combines a conflict`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md"))))
        repository.edits[room] = listOf(aConflict(id = 7))
        NoteEditorPresenter(room, "Súper.md", 7, {}, repository, clock, reader).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.isResolvingConflict).isTrue()
            assertThat(state.value.text).isEqualTo("- huevos" + NoteEditorPresenter.CONFLICT_SEPARATOR + "- pan")
            // Editing a combined text changes nothing else on screen (it's already unsaved), so save from the same state.
            state.eventSink(NoteEditorEvent.ValueChanged(typed("- huevos\n- pan")))
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

    // Note: tick checklist items, create from link, rename, delete

    /** Tapping a checklist item in the note saves the ticked note and sends it. */
    @Test
    fun `ticking a checklist item saves the note`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- [ ] leche\n- [ ] pan"))))
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            state.eventSink(NoteEvent.ToggleTask(1))
            awaitUntil { repository.savedEdits.isNotEmpty() }
            assertThat(repository.savedEdits).containsExactly("Súper.md" to "- [ ] leche\n- [x] pan")
            assertThat(repository.backgroundSyncs).containsExactly(room)
            cancelAndIgnoreRemainingEvents()
        }
    }

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

    /** A photo embedded in a note downloads by itself; tapping a file hands it to another app; a failed download explains. */
    @Test
    fun `embedded photos and files`() = runTest {
        val photo = java.nio.file.Files.createTempFile("luna", ".jpg").toFile()
        val repository = FakeNotesRepository(
            files = mutableMapOf(
                room to listOf(
                    aNote("Súper.md", "![[luna.jpg]]\nVer [[factura.pdf]]"),
                    aNote("attachments/luna.jpg").copy(isNote = false),
                    aNote("attachments/factura.pdf").copy(isNote = false),
                ),
            ),
        )
        repository.attachmentResult =
            { path -> if (path.endsWith(".jpg")) Result.success(photo) else Result.failure(NotesException.Network(IllegalStateException())) }
        NotePresenter(room, "Súper.md", RecordingNoteNavigator(), repository).test {
            val shown = consumeItemsUntilPredicate { it.embeds["luna.jpg"]?.file != null }.last()
            assertThat(shown.embeds["luna.jpg"]?.path).isEqualTo("attachments/luna.jpg")
            assertThat(shown.embeds["luna.jpg"]?.isImage).isTrue()

            shown.eventSink(NoteEvent.OpenAttachment("attachments/luna.jpg"))
            val opening = consumeItemsUntilPredicate { it.openFile != null }.last()
            assertThat(opening.openFile).isEqualTo(OpenFileRequest(photo.absolutePath, "luna.jpg"))
            opening.eventSink(NoteEvent.FileOpenHandled(opened = true))
            consumeItemsUntilPredicate { it.openFile == null }

            // "[[factura.pdf]]" is a file, not a note: tapping it opens it, here without a connection.
            shown.eventSink(NoteEvent.OpenNoteLink("factura.pdf"))
            assertThat(consumeItemsUntilPredicate { it.dialog != null }.last().dialog).isEqualTo(NoteDialog.AttachmentUnavailable)
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

    /** The text as if typed, with the cursor at its end. */
    private fun typed(text: String) = TextFieldValue(text, TextRange(text.length))

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
