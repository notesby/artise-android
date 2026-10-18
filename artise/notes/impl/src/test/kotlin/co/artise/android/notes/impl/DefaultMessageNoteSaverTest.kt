/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.impl.ui.FakeNotesRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.TimeZone

class DefaultMessageNoteSaverTest {
    /** "Guardar en Notas": a quoted note in "Mensajes guardados", named by date and sender, ending with who wrote it and when. */
    @Test
    fun `a message becomes a quoted note`() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val repository = FakeNotesRepository()
        val saver = DefaultMessageNoteSaver(repository, FakeStringProvider("Mensajes guardados"))
        // 17 Oct 2026, 14:32 UTC
        val path = saver.save(A_ROOM_ID, "Ana / Pérez", 1_792_247_520_000L, "Comprar pan\nY leche").getOrThrow()
        assertThat(path).isEqualTo("Mensajes guardados/2026-10-17 1432 Ana Pérez.md")
        assertThat(repository.createdNotes.single().second).isEqualTo("> Comprar pan\n> Y leche\n\n— Ana / Pérez, 17/10/2026 14:32\n")
    }
}
