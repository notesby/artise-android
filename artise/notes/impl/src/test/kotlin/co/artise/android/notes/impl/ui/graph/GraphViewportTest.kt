/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GraphViewportTest {
    private val center = Offset(500f, 800f)

    /** Pinching keeps the point between the fingers in place, instead of drifting toward the middle. */
    @Test
    fun `zoom keeps the focus point in place`() {
        val start = GraphViewport(scale = 1.5f, pan = Offset(40f, -30f))
        val focus = Offset(200f, 300f)
        // The unzoomed point currently drawn under the focus.
        val base = center + (focus - center - start.pan) / start.scale
        val zoomed = start.zoomBy(2f, focus, center)
        assertThat(zoomed.scale).isEqualTo(3f)
        val drawnAt = zoomed.toScreen(base, center)
        assertThat(drawnAt.x).isWithin(0.01f).of(focus.x)
        assertThat(drawnAt.y).isWithin(0.01f).of(focus.y)
    }

    /** Zoom stays between the limits, and the focus point still stays put when a limit is hit. */
    @Test
    fun `zoom is limited`() {
        val focus = Offset(100f, 100f)
        val base = center + (focus - center)
        val maxed = GraphViewport().zoomBy(100f, focus, center)
        assertThat(maxed.scale).isEqualTo(GraphViewport.MAX_SCALE)
        assertThat(maxed.toScreen(base, center).x).isWithin(0.01f).of(focus.x)
        assertThat(GraphViewport().zoomBy(0.01f, focus, center).scale).isEqualTo(GraphViewport.MIN_SCALE)
    }

    /** Zooming in then out by the same step around the same point returns to where it was; reset is the start. */
    @Test
    fun `zoom in and out round trip and reset`() {
        val start = GraphViewport(pan = Offset(10f, 20f))
        val back = start.zoomBy(GraphViewport.STEP, center, center).zoomBy(1 / GraphViewport.STEP, center, center)
        assertThat(back.scale).isWithin(0.0001f).of(1f)
        assertThat(back.pan.x).isWithin(0.01f).of(10f)
        assertThat(start.panBy(Offset(5f, 5f)).pan).isEqualTo(Offset(15f, 25f))
        assertThat(GraphViewport.RESET.isReset).isTrue()
        assertThat(start.isReset).isFalse()
    }
}
