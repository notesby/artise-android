/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesPreview
import co.artise.android.notes.impl.ui.document.DocumentProblem
import co.artise.android.notes.impl.ui.document.NotesDocumentEvent
import co.artise.android.notes.impl.ui.document.NotesDocumentPresenter
import co.artise.android.notes.impl.ui.folder.NotesFolderEntries
import co.artise.android.notes.impl.ui.folder.NotesFolderEntry
import co.artise.android.notes.impl.ui.folder.NotesFolderEvent
import co.artise.android.notes.impl.ui.folder.NotesFolderPresenter
import co.artise.android.notes.impl.ui.note.OpenFileRequest
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NotesPreviewsPresentersTest {
    private val room = RoomId("!abc:artise.co")

    private fun aRawFile(path: String) = LocalFile(path, version = "p1", size = 10, modified = 1, isNote = false, content = null, hasLocalEdits = false)

    @Test
    fun `a note's row shows how it starts`() {
        assertThat(NotesFolderEntries.firstLines("# Mole\n\nChiles y chocolate.\n\n- tomate\n")).isEqualTo("Mole\nChiles y chocolate.")
        assertThat(NotesFolderEntries.firstLines("---\ntype: person\naliases: [Mamá]\n---\n# Ana\nVive en Monterrey")).isEqualTo("Ana\nVive en Monterrey")
        assertThat(NotesFolderEntries.firstLines("- [ ] leche\n- [x] pan")).isEqualTo("leche\npan")
        assertThat(NotesFolderEntries.firstLines(null)).isEmpty()
        assertThat(NotesFolderEntries.firstLines("   \n\n")).isEmpty()
        val entries = NotesFolderEntries.of(listOf(aNote("Mole.md", "# Mole\nRico")), folder = "")
        assertThat((entries.single() as NotesFolderEntry.Note).firstLines).isEqualTo("Mole\nRico")
    }

    /** A photo or document's row asks for its preview once; the folder's state carries what came back. */
    @Test
    fun `folder loads a file's preview once`() = runTest {
        val picture = Files.createTempFile("preview", ".png").toFile()
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aRawFile("luna.jpg"), aRawFile("oferta.docx"))), seenPrivacyNotice = true)
        repository.previewResult = { path -> if (path == "luna.jpg") NotesPreview.Image(picture, 256, 192) else NotesPreview.None }
        NotesFolderPresenter(room, "", {}, repository).test {
            val state = consumeItemsUntilPredicate { !it.isRefreshing }.last()
            state.eventSink(NotesFolderEvent.LoadPreview("luna.jpg"))
            state.eventSink(NotesFolderEvent.LoadPreview("luna.jpg"))
            state.eventSink(NotesFolderEvent.LoadPreview("oferta.docx"))
            val loaded = consumeItemsUntilPredicate { it.previews.size == 2 }.last()
            assertThat(loaded.previews["luna.jpg"]).isEqualTo(NotesPreview.Image(picture, 256, 192))
            assertThat(loaded.previews["oferta.docx"]).isEqualTo(NotesPreview.None)
            assertThat(repository.previewRequests).containsExactly("luna.jpg" to 256, "oferta.docx" to 256)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document syncs then shows the file`() = runTest {
        val pdf = Files.createTempFile("doc", ".pdf").toFile()
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.success(pdf) }
        NotesDocumentPresenter(room, "docs/Recetario.pdf", repository, testCoroutineDispatchers()).test {
            val state = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(state.title).isEqualTo("Recetario.pdf")
            assertThat(state.kind).isEqualTo(NoteAttachment.Kind.PDF)
            assertThat(state.file).isEqualTo(pdf.absolutePath)
            assertThat(state.problem).isNull()
            assertThat(repository.syncCount).isEqualTo(1)

            state.eventSink(NotesDocumentEvent.OpenWith)
            val opening = consumeItemsUntilPredicate { it.openFile != null }.last()
            assertThat(opening.openFile).isEqualTo(OpenFileRequest(pdf.absolutePath, "Recetario.pdf"))
            opening.eventSink(NotesDocumentEvent.FileOpenHandled(opened = false))
            val noApp = consumeItemsUntilPredicate { it.problem == DocumentProblem.NO_APP }.last()
            assertThat(noApp.openFile).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document says when it is offline and retries`() = runTest {
        val repository = FakeNotesRepository()
        var attempts = 0
        repository.attachmentResult = {
            attempts++
            if (attempts == 1) Result.failure(NotesException.Network(IllegalStateException())) else Result.success(File("/tmp/luna.jpg"))
        }
        NotesDocumentPresenter(room, "Fotos/luna.jpg", repository, testCoroutineDispatchers()).test {
            val offline = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(offline.problem).isEqualTo(DocumentProblem.OFFLINE)
            assertThat(offline.kind).isEqualTo(NoteAttachment.Kind.IMAGE)
            offline.eventSink(NotesDocumentEvent.Retry)
            val shown = consumeItemsUntilPredicate { it.file != null }.last()
            assertThat(shown.problem).isNull()
            assertThat(shown.file).isEqualTo("/tmp/luna.jpg")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document reports other failures`() = runTest {
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.failure(NotesException.NotFound("gone")) }
        NotesDocumentPresenter(room, "docs/Recetario.pdf", repository, testCoroutineDispatchers()).test {
            assertThat(consumeItemsUntilPredicate { !it.isLoading }.last().problem).isEqualTo(DocumentProblem.FAILED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A CSV reads as its text, and find works on it without asking the server. */
    @Test
    fun `document shows a csv as text and finds in it`() = runTest {
        val csv = Files.createTempFile("gastos", ".csv").toFile().apply { writeText("fecha,monto\n2026-10-01,120\n2026-10-02,Camión 80\n") }
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.success(csv) }
        NotesDocumentPresenter(room, "gastos.csv", repository, testCoroutineDispatchers()).test {
            val shown = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(shown.kind).isEqualTo(NoteAttachment.Kind.TABLE)
            assertThat(shown.tableText).startsWith("fecha,monto")
            assertThat(shown.pdfFile).isNull()
            assertThat(shown.canFind).isTrue()
            shown.eventSink(NotesDocumentEvent.OpenFind)
            consumeItemsUntilPredicate { it.find != null }.last().eventSink(NotesDocumentEvent.FindQueryChanged("camion"))
            val found = consumeItemsUntilPredicate { it.find?.matches?.size == 1 }.last()
            assertThat(found.find?.isUnavailable).isFalse()
            assertThat(repository.textRequests).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An Office file shows the server's PDF conversion; "Open with" still hands over the original. */
    @Test
    fun `document shows an office file as its pdf`() = runTest {
        val docx = Files.createTempFile("oferta", ".docx").toFile()
        val pdf = Files.createTempFile("oferta", ".pdf").toFile()
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.success(docx) }
        repository.officePdfResult = { Result.success(pdf) }
        NotesDocumentPresenter(room, "docs/oferta.docx", repository, testCoroutineDispatchers()).test {
            val shown = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(shown.kind).isEqualTo(NoteAttachment.Kind.OFFICE)
            assertThat(shown.file).isEqualTo(docx.absolutePath)
            assertThat(shown.pdfFile).isEqualTo(pdf.absolutePath)
            assertThat(shown.canFind).isTrue()
            shown.eventSink(NotesDocumentEvent.OpenWith)
            assertThat(consumeItemsUntilPredicate { it.openFile != null }.last().openFile).isEqualTo(OpenFileRequest(docx.absolutePath, "oferta.docx"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** No conversion (an older server): the original is still there to open with another app, without find. */
    @Test
    fun `document without an office conversion offers open with`() = runTest {
        val docx = Files.createTempFile("oferta", ".docx").toFile()
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.success(docx) }
        NotesDocumentPresenter(room, "oferta.docx", repository, testCoroutineDispatchers()).test {
            val shown = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(shown.file).isEqualTo(docx.absolutePath)
            assertThat(shown.pdfFile).isNull()
            assertThat(shown.problem).isNull()
            assertThat(shown.canFind).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
