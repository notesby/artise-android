/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteEmbedRemoverTest {
    /** A photo alone in its paragraph goes with one blank line, leaving the paragraphs around it as they were. */
    @Test
    fun `an embed on its own line goes with its gap`() {
        assertThat(NoteEmbedRemover.remove("Hoy\n\n![[luna.jpg]]\n\nfin", "attachments/luna.jpg")).isEqualTo("Hoy\n\nfin")
        assertThat(NoteEmbedRemover.remove("Hoy\n\n![[luna.jpg]]\n\n", "attachments/luna.jpg")).isEqualTo("Hoy\n\n")
        assertThat(NoteEmbedRemover.remove("Hoy\n\n![[luna.jpg]]", "attachments/luna.jpg")).isEqualTo("Hoy")
    }

    /** The full path and a size (`|300`) are the same photo; in a line of text only the embed goes. */
    @Test
    fun `paths, sizes and embeds inside text`() {
        assertThat(NoteEmbedRemover.remove("![[attachments/luna.jpg|300]]\n\nfin", "attachments/luna.jpg")).isEqualTo("fin")
        assertThat(NoteEmbedRemover.remove("Mira ![[luna.jpg]] qué bonita", "attachments/luna.jpg")).isEqualTo("Mira qué bonita")
    }

    /** Other photos, and plain links to the photo, are left alone. */
    @Test
    fun `other embeds and links stay`() {
        val content = "![[sol.jpg]]\n\nVer [[luna.jpg]]"
        assertThat(NoteEmbedRemover.remove(content, "attachments/luna.jpg")).isEqualTo(content)
    }
}
