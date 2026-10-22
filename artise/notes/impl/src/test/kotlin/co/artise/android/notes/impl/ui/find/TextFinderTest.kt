/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.find

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextFinderTest {
    @Test
    fun `matches ignore case and accents`() {
        val pages = listOf("El camión llegó. Otro CAMION tarde.")
        val matches = TextFinder.find("camion", pages)
        assertThat(matches).containsExactly(FindMatch(0, 3, 9), FindMatch(0, 22, 28)).inOrder()
        // And the other way round: an accented query finds plain text
        assertThat(TextFinder.find("Camión", listOf("un camion"))).containsExactly(FindMatch(0, 3, 9))
        // Positions point into the original text
        assertThat(pages[0].substring(3, 9)).isEqualTo("camión")
    }

    @Test
    fun `matches across pages come in reading order`() {
        val pages = listOf("mole aquí", "nada", "Mole y más mole")
        assertThat(TextFinder.find("mole", pages)).containsExactly(
            FindMatch(0, 0, 4),
            FindMatch(2, 0, 4),
            FindMatch(2, 11, 15),
        ).inOrder()
    }

    @Test
    fun `blank queries and empty pages match nothing`() {
        assertThat(TextFinder.find("", listOf("texto"))).isEmpty()
        assertThat(TextFinder.find("   ", listOf("texto"))).isEmpty()
        assertThat(TextFinder.find("a", listOf("", ""))).isEmpty()
    }

    @Test
    fun `next and previous wrap around`() {
        assertThat(TextFinder.next(0, 3)).isEqualTo(1)
        assertThat(TextFinder.next(2, 3)).isEqualTo(0)
        assertThat(TextFinder.previous(0, 3)).isEqualTo(2)
        assertThat(TextFinder.previous(1, 3)).isEqualTo(0)
        assertThat(TextFinder.next(0, 0)).isEqualTo(0)
        assertThat(TextFinder.previous(0, 0)).isEqualTo(0)
    }

    @Test
    fun `folding keeps one character per character`() {
        val text = "Ñandú ÁÉÍ ß œ 日本"
        assertThat(TextFinder.fold(text)).hasLength(text.length)
        assertThat(TextFinder.fold("Camión")).isEqualTo("camion")
    }

    @Test
    fun `the snippet shows a little around the match`() {
        val text = "Primero se tuestan los chiles,\nluego el chocolate se derrite despacio en la cazuela de barro con el caldo."
        val match = TextFinder.find("chocolate", listOf(text)).single()
        val snippet = TextFinder.snippet(text, match, radius = 12)
        assertThat(snippet).isEqualTo("…s, luego el chocolate se derrite…")
    }
}
