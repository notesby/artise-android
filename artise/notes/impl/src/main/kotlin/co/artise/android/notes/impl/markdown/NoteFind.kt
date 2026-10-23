/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import co.artise.android.notes.impl.ui.find.TextFinder

/**
 * Finding words in a note as it reads, formatted. The note is drawn as many separate texts (headings, paragraphs, list
 * items, table rows), so each one finds its own matches and reports where it is on screen; the matches are counted in
 * reading order, top to bottom, and the current one is the nth of them.
 */
@Stable
class NoteFind {
    var query by mutableStateOf("")
        private set
    var current by mutableIntStateOf(0)
        private set

    private data class Run(val top: Float, val left: Float, val count: Int)

    private val runs = mutableStateMapOf<Any, Run>()

    val matchCount: Int get() = runs.values.sumOf { it.count }

    fun onQueryChange(query: String) {
        this.query = query
        current = 0
    }

    fun next() {
        current = TextFinder.next(current, matchCount)
    }

    fun previous() {
        current = TextFinder.previous(current, matchCount)
    }

    /** Where the current match's text is on screen (its top, in root coordinates), to scroll it into view. */
    fun currentTop(): Float? = locate()?.let { (key, _) -> runs[key]?.top }

    /** The index, among [key]'s own matches, of the current match when it is in that text. */
    internal fun currentIn(key: Any): Int? = locate()?.takeIf { it.first == key }?.second

    private fun locate(): Pair<Any, Int>? {
        var left = current
        for ((key, run) in runs.entries.sortedWith(compareBy({ it.value.top }, { it.value.left }))) {
            if (left < run.count) return key to left
            left -= run.count
        }
        return null
    }

    internal fun report(key: Any, top: Float, left: Float, count: Int) {
        if (count == 0) {
            runs.remove(key)
            return
        }
        val run = Run(top, left, count)
        // Only a real move or change is written: scrolling reports every frame.
        if (runs[key] != run) runs[key] = run
    }

    internal fun forget(key: Any) {
        runs.remove(key)
    }
}

/** The note's find, while its bar is open. */
@Suppress("CompositionLocalAllowlist")
val LocalNoteFind = staticCompositionLocalOf<NoteFind?> { null }

/**
 * [text] with the open find's matches painted, and a modifier that tells the find where this text is. Without an open
 * find (or a query) the text is unchanged.
 */
@Composable
internal fun rememberFindHighlights(text: AnnotatedString): Pair<AnnotatedString, Modifier> {
    val find = LocalNoteFind.current ?: return text to Modifier
    val key = remember { Any() }
    DisposableEffect(find, key) { onDispose { find.forget(key) } }
    val matches = remember(text.text, find.query) { TextFinder.find(find.query, listOf(text.text)) }
    val currentHere = find.currentIn(key)
    val matchColor = FIND_MATCH
    val currentColor = FIND_CURRENT
    val shown = remember(text, matches, currentHere) {
        if (matches.isEmpty()) {
            text
        } else {
            AnnotatedString.Builder(text).apply {
                matches.forEachIndexed { index, match ->
                    addStyle(SpanStyle(background = if (index == currentHere) currentColor else matchColor), match.start, match.end)
                }
            }.toAnnotatedString()
        }
    }
    val modifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        find.report(key, bounds.top, bounds.left, matches.size)
    }
    return shown to modifier
}

// Light and strong yellow, as browsers mark found text; readable on both themes' text colours.
private val FIND_MATCH = Color(0x66FFD54F)
private val FIND_CURRENT = Color(0xCCFFB300)
