package com.orbit.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContrastTest {
    @Test
    fun homeInformativeTextMeetsEnhancedContrastInBothThemes() {
        listOf(false, true).forEach { isDark ->
            val colors = defaultOrbitColorScheme(isDark)
            assertTrue(meetsTextReadability(colors.onBackground, colors.surfaceContainerLow, 7f))
            assertTrue(meetsTextReadability(colors.onSurfaceVariant, colors.surfaceContainerLow, 7f))
            assertTrue(meetsTextReadability(colors.onSurfaceVariant, colors.surfaceContainerHigh, 7f))
        }
    }

    @Test
    fun homeControlsAndSelectionMeetRequiredContrastInBothThemes() {
        listOf(false, true).forEach { isDark ->
            val colors = defaultOrbitColorScheme(isDark)
            assertTrue(meetsTextReadability(colors.onPrimary, colors.primary, 4.5f))
            assertTrue(meetsTextReadability(colors.onPrimaryContainer, colors.primaryContainer, 4.5f))
            assertTrue(meetsTextReadability(colors.outline, colors.surfaceContainerLow, 3f))
        }
    }
}
