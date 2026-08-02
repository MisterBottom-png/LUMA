package com.orbit.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmPressHapticsTest {
    @Test
    fun `accepted short stationary press produces feedback`() {
        assertTrue(
            shouldPerformCalmPressHaptic(
                pressAccepted = true,
                maximumDistance = 3f,
                durationMillis = 120,
                touchSlop = 8f,
                longPressTimeoutMillis = 500,
                multiplePointers = false,
            ),
        )
    }

    @Test
    fun `unaccepted blank area tap stays silent`() {
        assertFalse(
            shouldPerformCalmPressHaptic(
                pressAccepted = false,
                maximumDistance = 3f,
                durationMillis = 120,
                touchSlop = 8f,
                longPressTimeoutMillis = 500,
                multiplePointers = false,
            ),
        )
    }

    @Test
    fun `scroll drag stays silent`() {
        assertFalse(
            shouldPerformCalmPressHaptic(
                pressAccepted = true,
                maximumDistance = 20f,
                durationMillis = 180,
                touchSlop = 8f,
                longPressTimeoutMillis = 500,
                multiplePointers = false,
            ),
        )
    }

    @Test
    fun `long press stays silent`() {
        assertFalse(
            shouldPerformCalmPressHaptic(
                pressAccepted = true,
                maximumDistance = 2f,
                durationMillis = 500,
                touchSlop = 8f,
                longPressTimeoutMillis = 500,
                multiplePointers = false,
            ),
        )
    }

    @Test
    fun `multi touch stays silent`() {
        assertFalse(
            shouldPerformCalmPressHaptic(
                pressAccepted = true,
                maximumDistance = 2f,
                durationMillis = 120,
                touchSlop = 8f,
                longPressTimeoutMillis = 500,
                multiplePointers = true,
            ),
        )
    }
}
