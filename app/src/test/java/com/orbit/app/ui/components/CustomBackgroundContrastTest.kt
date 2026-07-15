package com.orbit.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBackgroundContrastTest {
    @Test
    fun lightModeUsesStrongThemeTonalProtectionThroughHomeHeader() {
        val tonalColor = Color(0xFFF7F3FB)

        val style = customBackgroundContrastStyle(
            isDark = false,
            tonalColor = tonalColor,
        )

        assertEquals(tonalColor, style.tonalColor)
        assertTrue(style.systemBarAlpha >= 0.90f)
        assertTrue(style.headerAlpha >= 0.85f)
        assertTrue(style.bodyAlpha >= 0.70f)
        assertTrue(style.headerStop >= 0.55f)
    }

    @Test
    fun darkModeUsesStrongThemeTonalProtectionThroughHomeHeader() {
        val tonalColor = Color(0xFF15121B)

        val style = customBackgroundContrastStyle(
            isDark = true,
            tonalColor = tonalColor,
        )

        assertEquals(tonalColor, style.tonalColor)
        assertTrue(style.systemBarAlpha >= 0.85f)
        assertTrue(style.headerAlpha >= 0.80f)
        assertTrue(style.bodyAlpha >= 0.65f)
        assertTrue(style.headerStop >= 0.55f)
    }

    @Test
    fun contrastProtectionRemainsLocalizedToTheTopOfTheImage() {
        val style = customBackgroundContrastStyle(
            isDark = false,
            tonalColor = Color.White,
        )

        assertTrue(style.endHeightFraction in 0.30f..0.40f)
        assertTrue(style.headerStop in 0f..1f)
        assertTrue(style.headerAlpha <= style.systemBarAlpha)
    }

    @Test
    fun customBackgroundStrengthensGlassWithoutHidingTheImage() {
        val presetNavigationAlpha = glassTintAlpha(
            style = GlassSurfaceStyle.Subtle,
            isDark = true,
            glassStrength = 0.72f,
            hasCustomBackground = false,
        )
        val customNavigationAlpha = glassTintAlpha(
            style = GlassSurfaceStyle.Subtle,
            isDark = true,
            glassStrength = 0.72f,
            hasCustomBackground = true,
        )
        val customCaptureAlpha = glassTintAlpha(
            style = GlassSurfaceStyle.Prominent,
            isDark = false,
            glassStrength = 0.72f,
            hasCustomBackground = true,
        )

        assertTrue(customNavigationAlpha > presetNavigationAlpha)
        assertTrue(customNavigationAlpha in 0.25f..0.40f)
        assertTrue(customCaptureAlpha in 0.40f..0.55f)
    }

    @Test
    fun customGlassTintRemainsTranslucent() {
        GlassSurfaceStyle.entries.forEach { style ->
            val alpha = glassTintAlpha(
                style = style,
                isDark = true,
                glassStrength = 1f,
                hasCustomBackground = true,
            )

            assertTrue(alpha <= 0.60f)
        }
    }
}
