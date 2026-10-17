package com.orbit.app.ui.components

import com.orbit.app.R
import com.orbit.app.domain.model.AppBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five built-in Capture backgrounds (proposal section 7.17, decisions D-23 and D-26). */
class CaptureBackgroundTest {
    @Test
    fun theSpecTableHasTheExactWashesAndResources() {
        data class Row(val bg: AppBackground, val light: Int, val dark: Int, val lightWash: Float, val darkWash: Float)

        val expected = listOf(
            Row(AppBackground.Limestone, R.drawable.bg_limestone_light, R.drawable.bg_limestone_dark, 0.10f, 0f),
            Row(AppBackground.HarbourMist, R.drawable.bg_harbour_mist_light, R.drawable.bg_harbour_mist_dark, 0.45f, 0f),
            Row(AppBackground.InkWash, R.drawable.bg_ink_wash_light, R.drawable.bg_ink_wash_dark, 0.10f, 0f),
            Row(AppBackground.PineFog, R.drawable.bg_pine_fog_light, R.drawable.bg_pine_fog_dark, 0.35f, 0f),
            Row(AppBackground.LinenLight, R.drawable.bg_linen_light_light, R.drawable.bg_linen_light_dark, 0.35f, 0f),
        )
        assertEquals(
            expected,
            TalleleBackgroundSpecs.map { Row(it.background, it.light, it.dark, it.lightWash, it.darkWash) },
        )
    }

    @Test
    fun everyBuiltInBackgroundHasASpecAndNoneHasNone() {
        assertEquals(
            AppBackground.entries.filter { it != AppBackground.None }.toSet(),
            TalleleBackgroundSpecs.map { it.background }.toSet(),
        )
        assertEquals(TalleleBackgroundSpecs.size, TalleleBackgroundSpecs.map { it.background }.distinct().size)
        assertNull(talleleBackgroundSpecFor(AppBackground.None))
    }

    @Test
    fun everyImageHasItsOwnLightAndDarkResource() {
        val all = TalleleBackgroundSpecs.flatMap { listOf(it.light, it.dark) }
        assertEquals("a resource is shared", all.size, all.toSet().size)
        TalleleBackgroundSpecs.forEach { spec ->
            assertNotEquals("${spec.background}", spec.light, spec.dark)
            assertTrue(spec.light != 0 && spec.dark != 0)
        }
    }

    @Test
    fun noneHasNoDrawable_othersFollowTheTheme() {
        listOf(false, true).forEach { assertNull(backgroundDrawableFor(AppBackground.None, it)) }
        TalleleBackgroundSpecs.forEach { spec ->
            assertEquals(spec.light, backgroundDrawableFor(spec.background, isDark = false))
            assertEquals(spec.dark, backgroundDrawableFor(spec.background, isDark = true))
            assertNotNull(backgroundDrawableFor(spec.background, isDark = false))
        }
    }

    @Test
    fun washesAreFractionsAndTheDarkImagesNeedNone() {
        TalleleBackgroundSpecs.forEach { spec ->
            assertTrue("${spec.background}", spec.lightWash in 0f..1f)
            assertTrue("${spec.background}", spec.darkWash in 0f..1f)
            assertEquals("${spec.background}", 0f, spec.darkWash)
            assertEquals(spec.lightWash, spec.washFor(isDark = false))
            assertEquals(spec.darkWash, spec.washFor(isDark = true))
        }
    }

    @Test
    fun harbourMistAndPineFogUseTheWashesRaisedByDecisionD23() {
        assertEquals(0.45f, talleleBackgroundSpecFor(AppBackground.HarbourMist)!!.lightWash)
        assertEquals(0.35f, talleleBackgroundSpecFor(AppBackground.PineFog)!!.lightWash)
    }
}
