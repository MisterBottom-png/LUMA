package com.orbit.app.ui.screens.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewContrastTest {
    @Test
    fun customBackgroundGetsStrongLightModeProtection() {
        val alpha = reviewBackdropAlpha(hasCustomBackground = true, isDark = false)

        assertTrue(alpha >= 0.94f)
    }

    @Test
    fun customBackgroundGetsStrongDarkModeProtection() {
        val alpha = reviewBackdropAlpha(hasCustomBackground = true, isDark = true)

        assertTrue(alpha >= 0.94f)
    }

    @Test
    fun presetBackgroundGetsAQuietReviewBackdrop() {
        assertEquals(0.74f, reviewBackdropAlpha(hasCustomBackground = false, isDark = false))
        assertEquals(0.78f, reviewBackdropAlpha(hasCustomBackground = false, isDark = true))
    }
}
