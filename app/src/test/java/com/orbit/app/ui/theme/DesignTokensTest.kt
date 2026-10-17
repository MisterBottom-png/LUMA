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
    fun `surface shapes follow the Limestone corner roles`() {
        assertEquals(RoundedCornerShape(8.dp), OrbitShapes.Small)
        assertEquals(RoundedCornerShape(12.dp), OrbitShapes.Standard)
        assertEquals(RoundedCornerShape(20.dp), OrbitShapes.Prominent)
        assertEquals(
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            OrbitShapes.Modal,
        )
    }

    @Test
    fun `Tallele shapes match section 6-3`() {
        assertEquals(RoundedCornerShape(20.dp), TalleleShapes.CaptureBox)
        assertEquals(RoundedCornerShape(12.dp), TalleleShapes.Card)
        assertEquals(RoundedCornerShape(8.dp), TalleleShapes.Chip)
        assertEquals(RoundedCornerShape(8.dp), TalleleShapes.SmallButton)
        assertEquals(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), TalleleShapes.Sheet)
        assertEquals(RoundedCornerShape(20.dp), TalleleShapes.TabBar)
    }

    @Test
    fun `Material shapes are provided on the Limestone scale`() {
        assertEquals(RoundedCornerShape(4.dp), TalleleMaterialShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), TalleleMaterialShapes.small)
        assertEquals(RoundedCornerShape(12.dp), TalleleMaterialShapes.medium)
        assertEquals(RoundedCornerShape(12.dp), TalleleMaterialShapes.large)
        assertEquals(RoundedCornerShape(24.dp), TalleleMaterialShapes.extraLarge)
    }

    @Test
    fun `motion roles match section 6-5 and stay calm ordered and finite`() {
        assertEquals(120, TalleleMotion.QuickMillis)
        assertEquals(220, TalleleMotion.SettleMillis)
        assertEquals(300, TalleleMotion.MoveMillis)
        assertEquals(180, TalleleMotion.TabsMillis)
        assertEquals(TalleleMotion.QuickMillis, OrbitMotion.QuickDurationMillis)
        assertEquals(TalleleMotion.SettleMillis, OrbitMotion.StandardDurationMillis)
        assertEquals(300, OrbitMotion.EmphasizedDurationMillis)
        assertTrue(OrbitMotion.QuickDurationMillis > 0)
        assertTrue(OrbitMotion.QuickDurationMillis < OrbitMotion.StandardDurationMillis)
        assertTrue(OrbitMotion.StandardDurationMillis < OrbitMotion.EmphasizedDurationMillis)
        assertTrue(TalleleMotion.SettleMillis in 200..240)
        assertTrue(OrbitMotion.PressedScale in 0.95f..1f)
        assertTrue(OrbitStateLayer.Selected in 0.12f..0.16f)
    }

    @Test
    fun `side margin is 20 dp and 16 dp under 360 dp`() {
        assertEquals(20.dp, talleleSideMargin(360))
        assertEquals(20.dp, talleleSideMargin(411))
        assertEquals(16.dp, talleleSideMargin(359))
        assertEquals(16.dp, talleleSideMargin(320))
    }

    @Test
    fun `layout constants match section 6-3`() {
        assertEquals(48.dp, TalleleDimens.MinTouchTarget)
        assertEquals(2.dp, TalleleDimens.InkRuleWidth)
        assertEquals(20.dp, TalleleDimens.SideMargin)
        assertEquals(16.dp, TalleleDimens.SideMarginCompact)
    }

    @Test
    fun `spacing stays on the 4 dp grid apart from the 2 dp hairline step`() {
        listOf(
            OrbitSpacing.Tiny, OrbitSpacing.Small, OrbitSpacing.Medium, OrbitSpacing.Large, OrbitSpacing.Comfortable,
            OrbitSpacing.ExtraLarge, OrbitSpacing.Huge, OrbitSpacing.Giant, OrbitSpacing.Display,
        ).forEach { assertEquals(0f, it.value % 4f, 0f) }
    }

    @Test
    fun `Russian uses the same Tallele typeface as other languages`() {
        assertEquals(TalleleSans, orbitFontFamilyFor("ru"))
        assertEquals(TalleleSans, orbitFontFamilyFor("et"))
        assertEquals(TalleleSans, orbitFontFamilyFor("en"))
    }
}
