/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesText
import co.artise.android.notes.api.NotesTextPage
import co.artise.android.notes.impl.ui.document.NotesDocumentEvent
import co.artise.android.notes.impl.ui.document.NotesDocumentPresenter
import co.artise.android.notes.impl.ui.editor.AttachmentReader
import co.artise.android.notes.impl.ui.editor.NoteEditorEvent
import co.artise.android.notes.impl.ui.editor.NoteEditorPresenter
import co.artise.android.notes.impl.ui.find.FindMatch
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class NotesFindPresentersTest {
    private val room = RoomId("!abc:artise.co")
    private val clock = FakeSystemClock()
    private val reader = AttachmentReader { error("not used") }

    private fun typed(text: String) = TextFieldValue(text, TextRange(text.length))

    /** The editor finds in the text as it is now, unsaved edits included, and the matches move with the text. */
    @Test
    fun `editor finds in unsaved text`() = runTest {
        val repository = FakeNotesRepository(files = mutableMapOf(room to listOf(aNote("Súper.md", "- leche"))))
        NoteEditorPresenter(room, "Súper.md", null, {}, repository, clock, reader).test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            loaded.eventSink(NoteEditorEvent.TogglePreview)
            consumeItemsUntilPredicate { it.isPreviewing }
            loaded.eventSink(NoteEditorEvent.ValueChanged(typed("- leche\n- Pan\n- más pan")))
            loaded.eventSink(NoteEditorEvent.OpenFind)
            val opened = consumeItemsUntilPredicate { it.find != null }.last()
            // Matches show in the text, so the preview closes
            assertThat(opened.isPreviewing).isFalse()
            opened.eventSink(NoteEditorEvent.FindQueryChanged("PAN"))
            val found = consumeItemsUntilPredicate { it.find?.matches?.isNotEmpty() == true }.last()
            assertThat(found.find?.matches).containsExactly(10..12, 20..22).inOrder()
            assertThat(found.find?.current).isEqualTo(0)
            found.eventSink(NoteEditorEvent.FindNext)
            assertThat(consumeItemsUntilPredicate { it.find?.current == 1 }.last().find?.current).isEqualTo(1)
            found.eventSink(NoteEditorEvent.FindNext)
            assertThat(consumeItemsUntilPredicate { it.find?.current == 0 }.last().find?.current).isEqualTo(0)
            found.eventSink(NoteEditorEvent.FindPrevious)
            assertThat(consumeItemsUntilPredicate { it.find?.current == 1 }.last().find?.current).isEqualTo(1)
            // More typing: the matches follow
            found.eventSink(NoteEditorEvent.ValueChanged(typed("- leche\n- Pan")))
            assertThat(consumeItemsUntilPredicate { it.find?.matches?.size == 1 }.last().find?.matches).containsExactly(10..12)
            found.eventSink(NoteEditorEvent.CloseFind)
            assertThat(consumeItemsUntilPredicate { it.find == null }.last().find).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document finds in the server's pages and moves between them`() = runTest {
        val pdf = Files.createTempFile("doc", ".pdf").toFile()
        val repository = FakeNotesRepository()
        repository.attachmentResult = { Result.success(pdf) }
        repository.documentTextResult = {
            Result.success(NotesText("docs/Recetario.pdf", "3f9c", listOf(NotesTextPage(2, "chiles y chocolate"), NotesTextPage(1, "Mole poblano. Más MOLE."))))
        }
        NotesDocumentPresenter(room, "docs/Recetario.pdf", repository, testCoroutineDispatchers()).test {
            val shown = consumeItemsUntilPredicate { it.file != null }.last()
            assertThat(shown.find).isNull()
            assertThat(repository.textRequests).isEmpty()
            shown.eventSink(NotesDocumentEvent.OpenFind)
            val ready = consumeItemsUntilPredicate { it.find?.pages?.isNotEmpty() == true }.last()
            // Pages in order by their number, whatever order the server listed them
            assertThat(ready.find?.pages).containsExactly("Mole poblano. Más MOLE.", "chiles y chocolate").inOrder()
            ready.eventSink(NotesDocumentEvent.FindQueryChanged("mole"))
            val found = consumeItemsUntilPredicate { it.find?.matches?.size == 2 }.last()
            assertThat(found.find?.matches).containsExactly(FindMatch(0, 0, 4), FindMatch(0, 18, 22)).inOrder()
            found.eventSink(NotesDocumentEvent.FindQueryChanged("CHOCOLATE"))
            val onPageTwo = consumeItemsUntilPredicate { it.find?.matches?.size == 1 }.last()
            assertThat(onPageTwo.find?.currentMatch).isEqualTo(FindMatch(1, 9, 18))
            onPageTwo.eventSink(NotesDocumentEvent.FindQueryChanged("zzz"))
            assertThat(consumeItemsUntilPredicate { it.find?.matches?.isEmpty() == true }.last().find?.currentMatch).isNull()
            // Closing and opening again doesn't ask the server again
            onPageTwo.eventSink(NotesDocumentEvent.CloseFind)
            consumeItemsUntilPredicate { it.find == null }
            onPageTwo.eventSink(NotesDocumentEvent.OpenFind)
            consumeItemsUntilPredicate { it.find != null }
            assertThat(repository.textRequests).containsExactly("docs/Recetario.pdf")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Too big (413), no text for this kind (415) or gone (404): the bar says search isn't available, no error. */
    @Test
    fun `document search is unavailable when the server has no text`() = runTest {
        for (failure in listOf(NotesException.TooBig("too_big"), NotesException.Server(415, "no_text"), NotesException.NotFound("gone"))) {
            val repository = FakeNotesRepository()
            repository.attachmentResult = { Result.success(Files.createTempFile("doc", ".pdf").toFile()) }
            repository.documentTextResult = { Result.failure(failure) }
            NotesDocumentPresenter(room, "docs/Recetario.pdf", repository, testCoroutineDispatchers()).test {
                consumeItemsUntilPredicate { it.file != null }.last().eventSink(NotesDocumentEvent.OpenFind)
                val state = consumeItemsUntilPredicate { it.find?.isUnavailable == true }.last()
                assertThat(state.find?.isLoading).isFalse()
                assertThat(state.problem).isNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
