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
        assertEquals(FontFamily.SansSerif, homeTypography(FontFamily.SansSerif).greeting.fontFamily)
        assertEquals(FontWeight.Normal, homeTypography(FontFamily.SansSerif).greeting.fontWeight)
        assertEquals(18.sp, homeTypography(FontFamily.SansSerif).greeting.fontSize)
        assertEquals(24.sp, homeTypography(FontFamily.SansSerif).greeting.lineHeight)

        assertEquals(FontWeight.Medium, homeTypography(FontFamily.SansSerif).userName.fontWeight)
        assertEquals(40.sp, homeTypography(FontFamily.SansSerif).userName.fontSize)
        assertEquals(44.sp, homeTypography(FontFamily.SansSerif).userName.lineHeight)
        assertEquals((-0.3).sp, homeTypography(FontFamily.SansSerif).userName.letterSpacing)

        assertEquals(FontWeight.SemiBold, homeTypography(FontFamily.SansSerif).calendarMonth.fontWeight)
        assertEquals(15.sp, homeTypography(FontFamily.SansSerif).calendarMonth.fontSize)
        assertEquals(20.sp, homeTypography(FontFamily.SansSerif).calendarMonth.lineHeight)
        assertEquals(FontWeight.Normal, homeTypography(FontFamily.SansSerif).calendarWeek.fontWeight)
        assertEquals(15.sp, homeTypography(FontFamily.SansSerif).calendarWeek.fontSize)
        assertEquals(20.sp, homeTypography(FontFamily.SansSerif).calendarWeek.lineHeight)
        assertEquals(FontWeight.Medium, homeTypography(FontFamily.SansSerif).calendarWeekday.fontWeight)
        assertEquals(12.sp, homeTypography(FontFamily.SansSerif).calendarWeekday.fontSize)
        assertEquals(16.sp, homeTypography(FontFamily.SansSerif).calendarWeekday.lineHeight)
        assertEquals(FontWeight.Medium, homeTypography(FontFamily.SansSerif).calendarDate.fontWeight)
        assertEquals(20.sp, homeTypography(FontFamily.SansSerif).calendarDate.fontSize)
        assertEquals(26.sp, homeTypography(FontFamily.SansSerif).calendarDate.lineHeight)
        assertEquals("tnum", homeTypography(FontFamily.SansSerif).calendarDate.fontFeatureSettings)

        assertEquals(FontWeight.Normal, homeTypography(FontFamily.SansSerif).capturePlaceholder.fontWeight)
        assertEquals(18.sp, homeTypography(FontFamily.SansSerif).capturePlaceholder.fontSize)
        assertEquals(26.sp, homeTypography(FontFamily.SansSerif).capturePlaceholder.lineHeight)
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
        assertEquals("tnum", calendarTypography(FontFamily.SansSerif).monthDate.fontFeatureSettings)
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

    @Test
    fun `Russian uses the system font, other languages use the Tallele typeface`() {
        assertEquals(FontFamily.SansSerif, orbitFontFamilyFor("ru"))
        assertEquals(TalleleSans, orbitFontFamilyFor("et"))
        assertEquals(TalleleSans, orbitFontFamilyFor("en"))
    }
}
