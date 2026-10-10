package com.orbit.app.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeImeLayoutTest {
    @Test
    fun `overflowing home content reveals the capture card when the IME opens`() {
        assertTrue(
            shouldRevealHomeCapture(
                imeVisible = true,
                requiresVerticalScroll = true,
            ),
        )
    }

    @Test
    fun `home content keeps its resting position without IME overflow`() {
        assertFalse(
            shouldRevealHomeCapture(
                imeVisible = false,
                requiresVerticalScroll = true,
            ),
        )
        assertFalse(
            shouldRevealHomeCapture(
                imeVisible = true,
                requiresVerticalScroll = false,
            ),
        )
    }
}
