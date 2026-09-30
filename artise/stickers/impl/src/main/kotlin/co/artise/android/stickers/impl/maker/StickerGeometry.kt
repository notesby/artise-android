/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.stickers.impl.maker

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in pixels: [right] and [bottom] are exclusive. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** The sizes behind a sticker: what to keep of a cut-out, and how big it ends up. */
internal object StickerGeometry {
    /** Stickers are at most this wide and tall: sharp on any phone, small to send. */
    const val MAX_SIDE = 512

    /** Pixels less opaque than this are background left around a cut-out. */
    private const val VISIBLE_ALPHA = 16

    /**
     * The smallest box around the visible pixels ([alphaAt] gives a pixel's opacity, 0-255), with [padding] pixels
     * of air around it; `null` when nothing is visible (the cut-out found nothing).
     */
    fun visibleBox(width: Int, height: Int, padding: Int, alphaAt: (x: Int, y: Int) -> Int): PixelBox? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (alphaAt(x, y) >= VISIBLE_ALPHA) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        if (right < 0) return null
        return PixelBox(
            left = max(0, left - padding),
            top = max(0, top - padding),
            right = min(width, right + 1 + padding),
            bottom = min(height, bottom + 1 + padding),
        )
    }

    /** The centered square of a picture, for a photo used whole. */
    fun centerSquare(width: Int, height: Int): PixelBox {
        val side = min(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return PixelBox(left, top, left + side, top + side)
    }

    /** [width] × [height] scaled down to fit within [MAX_SIDE], keeping its shape; never scaled up. */
    fun fit(width: Int, height: Int): Pair<Int, Int> {
        val scale = min(1f, MAX_SIDE.toFloat() / max(width, height))
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
