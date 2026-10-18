/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.MediaFile
import co.artise.android.notes.api.MediaInUseException
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.impl.A_ROOM
import co.artise.android.notes.impl.ui.media.MediaDialog
import co.artise.android.notes.impl.ui.media.MediaFilter
import co.artise.android.notes.impl.ui.media.NotesMediaEvent
import co.artise.android.notes.impl.ui.media.NotesMediaPresenter
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotesMediaPresenterTest {
    private fun repository() = FakeNotesRepository().apply {
        media += MediaFile("attachments/luna.jpg", 2000, 0, listOf("Mole.md"), upload = null)
        media += MediaFile("attachments/factura.pdf", 5000, 0, emptyList(), upload = null)
    }

    /** Unused files come first, and the "Unused" filter shows only those, with the space they take. */
    @Test
    fun `unused files are easy to find`() = runTest {
        NotesMediaPresenter(RoomId(A_ROOM), repository()).test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            assertThat(loaded.items.map { it.name }).containsExactly("factura.pdf", "luna.jpg").inOrder()
            assertThat(loaded.unusedCount).isEqualTo(1)
            assertThat(loaded.unusedBytes).isEqualTo(5000)
            loaded.eventSink(NotesMediaEvent.SetFilter(MediaFilter.UNUSED))
            assertThat(consumeItemsUntilPredicate { it.filter == MediaFilter.UNUSED }.last().items.map { it.name }).containsExactly("factura.pdf")
        }
    }

    /** A file in use asks before taking it out of its notes; confirming deletes it that way. */
    @Test
    fun `a file in use is removed from notes only after confirming`() = runTest {
        val repository = repository()
        NotesMediaPresenter(RoomId(A_ROOM), repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            loaded.eventSink(NotesMediaEvent.Delete(loaded.items.first { it.name == "luna.jpg" }))
            val asking = consumeItemsUntilPredicate { it.dialog != null }.last()
            assertThat(asking.dialog).isInstanceOf(MediaDialog.ConfirmRemoveAndDelete::class.java)
            asking.eventSink(NotesMediaEvent.ConfirmDelete)
            consumeItemsUntilPredicate { state -> state.items.none { it.name == "luna.jpg" } }
        }
        assertThat(repository.deletedMedia).containsExactly("attachments/luna.jpg" to true)
    }

    /** A file that looked unused but a note uses (the server said so) isn't deleted: the notes are shown. */
    @Test
    fun `server knows best`() = runTest {
        val repository = repository().apply {
            deleteMediaResult = { _, removeFromNotes -> if (removeFromNotes) Result.success(Unit) else Result.failure(MediaInUseException(listOf("Viaje.md"))) }
        }
        NotesMediaPresenter(RoomId(A_ROOM), repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            loaded.eventSink(NotesMediaEvent.Delete(loaded.items.first { it.name == "factura.pdf" }))
            consumeItemsUntilPredicate { it.dialog is MediaDialog.ConfirmDelete }.last().eventSink(NotesMediaEvent.ConfirmDelete)
            val inUse = consumeItemsUntilPredicate { it.dialog is MediaDialog.ConfirmRemoveAndDelete }.last().dialog as MediaDialog.ConfirmRemoveAndDelete
            assertThat(inUse.usedBy).containsExactly("Viaje.md")
        }
        assertThat(repository.deletedMedia).isEmpty()
    }

    /** Without a connection nothing is deleted, and the person is told why. */
    @Test
    fun `offline deletes are explained`() = runTest {
        val repository = repository().apply { deleteMediaResult = { _, _ -> Result.failure(NotesException.Network(IllegalStateException())) } }
        NotesMediaPresenter(RoomId(A_ROOM), repository).test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            loaded.eventSink(NotesMediaEvent.DeleteUnused)
            consumeItemsUntilPredicate { it.dialog is MediaDialog.ConfirmDeleteUnused }.last().eventSink(NotesMediaEvent.ConfirmDeleteUnused)
            assertThat(consumeItemsUntilPredicate { it.dialog == MediaDialog.Offline }.last().items).hasSize(2)
        }
    }
}
