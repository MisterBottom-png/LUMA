package com.orbit.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbit.app.domain.model.AppTextColor
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun warmIvoryUsesDarkReadableTextInLightMode() {
        val palette = textPalette(
            textColor = AppTextColor.WarmIvory,
            isDark = false,
            defaultPrimary = Color.Black,
        )

        assertTrue(palette.primary.luminance() < 0.05f)
        assertTrue(palette.secondary.luminance() < 0.10f)
    }
}
