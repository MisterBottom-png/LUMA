package com.orbit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun `spacing scale keeps documented dimensions`() {
        assertEquals(2.dp, OrbitSpacing.ExtraSmall)
        assertEquals(8.dp, OrbitSpacing.Small)
        assertEquals(12.dp, OrbitSpacing.Medium)
        assertEquals(16.dp, OrbitSpacing.Large)
        assertEquals(24.dp, OrbitSpacing.ExtraLarge)
        assertEquals(32.dp, OrbitSpacing.Huge)
    }

    @Test
    fun `surface shapes keep documented corner roles`() {
        assertEquals(RoundedCornerShape(14.dp), OrbitShapes.Small)
        assertEquals(RoundedCornerShape(18.dp), OrbitShapes.Standard)
        assertEquals(RoundedCornerShape(28.dp), OrbitShapes.Prominent)
        assertEquals(
            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            OrbitShapes.Modal,
        )
    }

    @Test
    fun `motion roles stay calm ordered and finite`() {
        assertTrue(OrbitMotion.QuickDurationMillis > 0)
        assertTrue(OrbitMotion.QuickDurationMillis < OrbitMotion.StandardDurationMillis)
        assertTrue(OrbitMotion.StandardDurationMillis < OrbitMotion.EmphasizedDurationMillis)
        assertTrue(OrbitMotion.PressedScale in 0.95f..1f)
        assertTrue(OrbitStateLayer.Selected in 0.12f..0.16f)
    }
}
