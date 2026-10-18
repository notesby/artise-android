/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * How the map is zoomed and moved. A point at [base] (unzoomed, in screen pixels) is drawn at
 * `center + (base - center) * scale + pan`, where `center` is the middle of the map.
 */
@Immutable
data class GraphViewport(
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero,
) {
    val isReset: Boolean get() = this == RESET

    fun toScreen(base: Offset, center: Offset): Offset = center + (base - center) * scale + pan

    /** Zooms by [factor] keeping the point under [focus] (e.g. between the fingers) where it is. */
    fun zoomBy(factor: Float, focus: Offset, center: Offset): GraphViewport {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = newScale / scale
        // The point under the focus, relative to the center, before the zoom: (focus - center - pan) / scale.
        // After it, it must still be under the focus: pan' = focus - center - (focus - center - pan) * applied.
        val fromCenter = focus - center
        return GraphViewport(newScale, fromCenter - (fromCenter - pan) * applied)
    }

    fun panBy(delta: Offset): GraphViewport = copy(pan = pan + delta)

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 6f

        /** How much the zoom buttons and a double tap zoom in or out. */
        const val STEP = 1.6f

        /** The whole map, as first shown. */
        val RESET = GraphViewport()
    }
}
