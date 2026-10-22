/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.find

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Paints the editor's find matches over another transformation's output: every match lightly, the current one strongly.
 * Match positions are in the original text; the inner transformation's offset mapping moves them to what is shown.
 */
class FindHighlightTransformation(
    private val inner: VisualTransformation,
    private val matches: List<IntRange>,
    private val current: Int,
    private val matchColor: Color,
    private val currentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = inner.filter(text)
        if (matches.isEmpty()) return transformed
        val spans = matches.mapIndexed { index, range ->
            val start = transformed.offsetMapping.originalToTransformed(range.first)
            val end = transformed.offsetMapping.originalToTransformed(range.last + 1)
            AnnotatedString.Range(SpanStyle(background = if (index == current) currentColor else matchColor), start, end)
        }.filter { it.start < it.end }
        val shown = transformed.text
        return TransformedText(AnnotatedString(shown.text, shown.spanStyles + spans, shown.paragraphStyles), transformed.offsetMapping)
    }

    override fun equals(other: Any?): Boolean =
        other is FindHighlightTransformation && other.inner == inner && other.matches == matches && other.current == current &&
            other.matchColor == matchColor && other.currentColor == currentColor

    override fun hashCode(): Int = listOf(inner, matches, current, matchColor, currentColor).hashCode()
}
