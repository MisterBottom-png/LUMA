package com.orbit.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TalleleTypographyTest {
    private val t = talleleTypography()

    private fun assertStyle(name: String, style: TextStyle, family: FontFamily, size: Int, line: Int, weight: FontWeight) {
        assertSame("$name family", family, style.fontFamily)
        assertEquals("$name size", size.sp, style.fontSize)
        assertEquals("$name line height", line.sp, style.lineHeight)
        assertEquals("$name weight", weight, style.fontWeight)
    }

    @Test
    fun `roles match section 6-2`() {
        assertStyle("screenTitle", t.screenTitle, TalleleSans, 24, 32, FontWeight.SemiBold)
        assertStyle("itemTitle", t.itemTitle, TalleleSerif, 24, 34, FontWeight.Medium)
        assertStyle("captureText", t.captureText, TalleleSerif, 20, 30, FontWeight.Normal)
        assertStyle("listWords", t.listWords, TalleleSerif, 17, 26, FontWeight.Normal)
        assertStyle("body", t.body, TalleleSans, 16, 23, FontWeight.Normal)
        assertStyle("bodyStrong", t.bodyStrong, TalleleSans, 16, 23, FontWeight.SemiBold)
        assertStyle("control", t.control, TalleleSans, 15, 23, FontWeight.SemiBold)
        assertStyle("metadata", t.metadata, TalleleSans, 14, 20, FontWeight.Normal)
        assertStyle("tabLabel", t.tabLabel, TalleleSans, 12, 16, FontWeight.SemiBold)
    }

    @Test
    fun `the person's words are serif and the app's words are sans`() {
        listOf(t.itemTitle, t.captureText, t.listWords).forEach { assertSame(TalleleSerif, it.fontFamily) }
        listOf(t.screenTitle, t.body, t.bodyStrong, t.control, t.metadata, t.tabLabel, t.timeTabular)
            .forEach { assertSame(TalleleSans, it.fontFamily) }
        assertNotEquals(TalleleSans, TalleleSerif)
    }

    @Test
    fun `times use tabular numbers and others do not`() {
        assertEquals("tnum", t.timeTabular.fontFeatureSettings)
        assertEquals(t.metadata.fontSize, t.timeTabular.fontSize)
        assertEquals(t.metadata.lineHeight, t.timeTabular.lineHeight)
        assertEquals(t.metadata.fontWeight, t.timeTabular.fontWeight)
        assertNull(t.metadata.fontFeatureSettings)
    }

    @Test
    fun `sizes are sp so text grows with the font scale and nothing is below 12`() {
        val all = listOf(t.screenTitle, t.itemTitle, t.captureText, t.listWords, t.body, t.bodyStrong, t.control, t.metadata, t.tabLabel, t.timeTabular)
        all.forEach {
            assertEquals(TextUnitType.Sp, it.fontSize.type)
            assertEquals(TextUnitType.Sp, it.lineHeight.type)
            assertTrue(it.fontSize.value >= 12f)
            assertTrue(it.lineHeight.value > it.fontSize.value)
            assertTrue("no light weights", it.fontWeight!!.weight >= 400)
        }
        // Only the tab label may be 12 sp; everything else is at least 14 sp.
        all.filter { it !== t.tabLabel }.forEach { assertTrue(it.fontSize.value >= 14f) }
    }

    @Test
    fun `Russian gets the same family as English and Estonian`() {
        assertSame(orbitFontFamilyFor("en"), orbitFontFamilyFor("ru"))
        assertSame(orbitFontFamilyFor("et"), orbitFontFamilyFor("ru"))
        assertSame(TalleleSans, orbitFontFamilyFor("ru"))
        assertNotEquals(FontFamily.SansSerif, orbitFontFamilyFor("ru"))
    }

    @Test
    fun `Material typography is Noto Sans throughout with the section 6-2 sizes`() {
        val m = OrbitMaterialTypography
        val styles = listOf(
            m.displayLarge, m.displayMedium, m.displaySmall, m.headlineLarge, m.headlineMedium, m.headlineSmall,
            m.titleLarge, m.titleMedium, m.titleSmall, m.bodyLarge, m.bodyMedium, m.bodySmall,
            m.labelLarge, m.labelMedium, m.labelSmall,
        )
        styles.forEach {
            assertSame(TalleleSans, it.fontFamily)
            assertTrue("no light weights", it.fontWeight!!.weight >= 400)
            assertTrue("labels at least 12 sp", it.fontSize.value >= 12f)
            assertNotNull(it.lineHeight)
            assertTrue("modest tracking", it.letterSpacing.value in -0.2f..0.3f)
        }
        assertEquals(24.sp, m.headlineMedium.fontSize)
        assertEquals(32.sp, m.headlineMedium.lineHeight)
        assertEquals(FontWeight.SemiBold, m.headlineMedium.fontWeight)
        assertEquals(16.sp, m.bodyLarge.fontSize)
        assertEquals(23.sp, m.bodyLarge.lineHeight)
        assertEquals(15.sp, m.bodyMedium.fontSize)
        assertEquals(23.sp, m.bodyMedium.lineHeight)
        assertEquals(14.sp, m.bodySmall.fontSize)
        assertEquals(20.sp, m.bodySmall.lineHeight)
        assertEquals(12.sp, m.labelSmall.fontSize)
        assertTrue(m.labelMedium.fontSize.value >= 13f)
    }

    @Test
    fun `home and calendar typography use the supplied family`() {
        val home = homeTypography(TalleleSans)
        val calendar = calendarTypography(TalleleSans)
        assertSame(TalleleSans, home.greeting.fontFamily)
        assertSame(TalleleSans, calendar.monthHeader.fontFamily)
        assertEquals("tnum", calendar.timelineHour.fontFeatureSettings)
    }
}
