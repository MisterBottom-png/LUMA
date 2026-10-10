package com.orbit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun `home typography keeps the semantic display calendar and capture targets`() {
        assertEquals(FontFamily.SansSerif, HomeTypography.greeting.fontFamily)
        assertEquals(FontWeight.Normal, HomeTypography.greeting.fontWeight)
        assertEquals(18.sp, HomeTypography.greeting.fontSize)
        assertEquals(24.sp, HomeTypography.greeting.lineHeight)

        assertEquals(FontWeight.Medium, HomeTypography.userName.fontWeight)
        assertEquals(40.sp, HomeTypography.userName.fontSize)
        assertEquals(44.sp, HomeTypography.userName.lineHeight)
        assertEquals((-0.3).sp, HomeTypography.userName.letterSpacing)

        assertEquals(FontWeight.SemiBold, HomeTypography.calendarMonth.fontWeight)
        assertEquals(15.sp, HomeTypography.calendarMonth.fontSize)
        assertEquals(20.sp, HomeTypography.calendarMonth.lineHeight)
        assertEquals(FontWeight.Normal, HomeTypography.calendarWeek.fontWeight)
        assertEquals(15.sp, HomeTypography.calendarWeek.fontSize)
        assertEquals(20.sp, HomeTypography.calendarWeek.lineHeight)
        assertEquals(FontWeight.Medium, HomeTypography.calendarWeekday.fontWeight)
        assertEquals(12.sp, HomeTypography.calendarWeekday.fontSize)
        assertEquals(16.sp, HomeTypography.calendarWeekday.lineHeight)
        assertEquals(FontWeight.Medium, HomeTypography.calendarDate.fontWeight)
        assertEquals(20.sp, HomeTypography.calendarDate.fontSize)
        assertEquals(26.sp, HomeTypography.calendarDate.lineHeight)
        assertEquals("tnum", HomeTypography.calendarDate.fontFeatureSettings)

        assertEquals(FontWeight.Normal, HomeTypography.capturePlaceholder.fontWeight)
        assertEquals(18.sp, HomeTypography.capturePlaceholder.fontSize)
        assertEquals(26.sp, HomeTypography.capturePlaceholder.lineHeight)
    }

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
    fun `calendar date typography uses tabular figures`() {
        assertEquals("tnum", CalendarTypography.monthDate.fontFeatureSettings)
        assertEquals(40.dp, CalendarDimensions.DateVisualDiameter)
        assertEquals(52.dp, CalendarDimensions.TimelineTimeColumnWidth)
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
