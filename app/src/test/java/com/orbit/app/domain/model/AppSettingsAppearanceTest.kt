package com.orbit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsAppearanceTest {
    @Test
    fun resetAppearanceRestoresVisualDefaultsAndPreservesOtherPreferences() {
        val customized = AppSettings(
            userName = "test user",
            themeMode = SettingsThemeMode.Dark,
            timeFormatMode = SettingsTimeFormatMode.TwentyFourHour,
            backgroundPreset = BackgroundPreset.NightOrbit,
            customBackgroundUri = "content://local/background",
            backgroundBlur = BackgroundBlur.Strong,
            backgroundDim = 1f,
            glassPreference = GlassPreference.Subtle,
            accentColor = AppAccentColor.Ocean,
            textColor = AppTextColor.Forest,
            staleLoopDays = 14,
            aiMode = AiMode.GeminiApi,
        )

        val reset = customized.withDefaultAppearance()
        val defaults = AppSettings()

        assertEquals(defaults.themeMode, reset.themeMode)
        assertEquals(defaults.backgroundPreset, reset.backgroundPreset)
        assertNull(reset.customBackgroundUri)
        assertEquals(defaults.backgroundBlur, reset.backgroundBlur)
        assertEquals(defaults.backgroundDim, reset.backgroundDim)
        assertEquals(defaults.glassPreference, reset.glassPreference)
        assertEquals(defaults.accentColor, reset.accentColor)
        assertEquals(defaults.textColor, reset.textColor)
        assertEquals(customized.userName, reset.userName)
        assertEquals(customized.timeFormatMode, reset.timeFormatMode)
        assertEquals(customized.staleLoopDays, reset.staleLoopDays)
        assertEquals(customized.aiMode, reset.aiMode)
    }
}
