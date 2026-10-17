package com.orbit.app.ui.components.parts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keep mark is a dot settling into a shallow tray (proposal 6.6). Pure geometry, no Compose. */
class KeepMarkGeometryTest {
    private val g = keepMarkGeometry()

    @Test
    fun everythingFitsInsideTheUnitSquare() {
        val half = g.strokeWidth / 2f
        assertTrue(g.dotCenter.x - g.dotRadius >= 0f && g.dotCenter.x + g.dotRadius <= 1f)
        assertTrue(g.dotCenter.y - g.dotRadius >= 0f)
        assertTrue(g.trayStart.x - half >= 0f && g.trayEnd.x + half <= 1f)
        assertTrue(g.trayBottomY + half <= 1f)
    }

    @Test
    fun dotIsCentredAboveTheTray() {
        assertEquals(g.trayCenterX, g.dotCenter.x, 0.0001f)
        assertEquals(0.5f, g.dotCenter.x, 0.0001f)
        // The whole dot sits above the tray's ends, so it reads as about to settle in.
        assertTrue(g.dotBottomY < g.trayStart.y)
        assertTrue(g.dotBottomY < g.trayEnd.y)
    }

    @Test
    fun trayIsASymmetricShallowArc() {
        assertEquals(g.trayStart.y, g.trayEnd.y, 0.0001f)
        assertEquals(g.trayCenterX, g.trayControl.x, 0.0001f)
        // The middle sits below the ends (it holds the dot) but only a little: depth under a third of the width.
        assertTrue(g.trayBottomY > g.trayStart.y)
        assertTrue("tray depth ${g.trayDepthRatio}", g.trayDepthRatio in 0.05f..0.33f)
    }

    @Test
    fun strokeAndDotAreVisibleAtSmallSizes() {
        // At 24 dp the stroke is at least 2 dp, so the mark stays legible.
        assertTrue(g.strokeWidth * 24f >= 2f)
        assertTrue(g.dotRadius * 24f >= 3f)
    }
}
