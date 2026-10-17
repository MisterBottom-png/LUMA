package com.orbit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LimestoneAppearanceTest {
    @Test
    fun accentMappingCoversEveryOldValue() {
        assertEquals(AppAccent.Ink, AppAccentColor.InkPaper.toAppAccent())
        assertEquals(AppAccent.Ink, AppAccentColor.Amber.toAppAccent())
        assertEquals(AppAccent.Harbour, AppAccentColor.Ocean.toAppAccent())
        assertEquals(AppAccent.Moss, AppAccentColor.Sage.toAppAccent())
        assertEquals(AppAccent.Plum, AppAccentColor.LumaViolet.toAppAccent())
        assertEquals(AppAccent.Plum, AppAccentColor.Rose.toAppAccent())
        assertEquals(6, AppAccentColor.values().size)
    }

    @Test
    fun glassEffectMappingCoversEveryOldValue() {
        assertEquals(TabBarStyle.Glass, GlassEffect.Strong.toTabBarStyle())
        assertEquals(TabBarStyle.Frosted, GlassEffect.Soft.toTabBarStyle())
        assertEquals(TabBarStyle.Solid, GlassEffect.Off.toTabBarStyle())
        assertEquals(3, GlassEffect.values().size)
    }

    @Test
    fun everyBackgroundPresetBecomesNoneWithOrWithoutOwnPhoto() {
        assertEquals(5, BackgroundPreset.values().size)
        for (preset in BackgroundPreset.values()) {
            assertEquals(preset.name, AppBackground.None, appBackgroundFor(preset, null))
            assertEquals(preset.name, AppBackground.None, appBackgroundFor(preset, "content://local/background"))
        }
    }

    @Test
    fun themeModePassesThroughUnchanged() {
        assertEquals(3, SettingsThemeMode.values().size)
        for (mode in SettingsThemeMode.values()) {
            assertEquals(mode, AppSettings(themeMode = mode).limestoneAppearance().theme)
        }
        assertEquals(SettingsThemeMode.Auto, AppSettings(themeMode = SettingsThemeMode.Auto).limestoneAppearance().theme)
        assertEquals(SettingsThemeMode.Light, AppSettings(themeMode = SettingsThemeMode.Light).limestoneAppearance().theme)
        assertEquals(SettingsThemeMode.Dark, AppSettings(themeMode = SettingsThemeMode.Dark).limestoneAppearance().theme)
    }

    @Test
    fun defaultSettingsMapToDefaultLimestoneAppearance() {
        val derived = AppSettings().limestoneAppearance()
        assertEquals(LimestoneAppearance(), derived)
        assertEquals(SettingsThemeMode.Auto, derived.theme)
        assertEquals(AppAccent.Ink, derived.accent)
        assertEquals(AppBackground.None, derived.background)
        assertEquals(TabBarStyle.Glass, derived.tabBar)
    }

    @Test
    fun droppedFieldsDoNotChangeTheResult() {
        val base = AppSettings(
            themeMode = SettingsThemeMode.Dark,
            accentColor = AppAccentColor.Sage,
            glassEffect = GlassEffect.Soft,
        )
        val expected = base.limestoneAppearance()

        for (color in AppTextColor.values()) {
            assertEquals(expected, base.copy(textColor = color).limestoneAppearance())
        }
        for (mode in AppearancePaletteMode.values()) {
            assertEquals(expected, base.copy(paletteMode = mode).limestoneAppearance())
        }
        for (blur in BackgroundBlur.values()) {
            assertEquals(expected, base.copy(backgroundBlur = blur).limestoneAppearance())
        }
        for (dim in listOf(0f, 1f)) {
            assertEquals(expected, base.copy(backgroundDim = dim).limestoneAppearance())
        }
        for (mode in BackgroundDimmingMode.values()) {
            assertEquals(expected, base.copy(backgroundDimmingMode = mode).limestoneAppearance())
        }
        for (preference in GlassPreference.values()) {
            assertEquals(expected, base.copy(glassPreference = preference).limestoneAppearance())
        }
    }

    @Test
    fun ownPhotoAndPresetDoNotChangeTheResult() {
        val base = AppSettings(accentColor = AppAccentColor.Rose)
        val expected = base.limestoneAppearance()
        for (preset in BackgroundPreset.values()) {
            assertEquals(expected, base.copy(backgroundPreset = preset).limestoneAppearance())
            assertEquals(
                expected,
                base.copy(backgroundPreset = preset, customBackgroundUri = "content://local/background")
                    .limestoneAppearance(),
            )
        }
    }

    @Test
    fun conversionIsPureAndIdempotent() {
        val settings = AppSettings(
            themeMode = SettingsThemeMode.Light,
            accentColor = AppAccentColor.Ocean,
            glassEffect = GlassEffect.Off,
            backgroundPreset = BackgroundPreset.NightOrbit,
            customBackgroundUri = "content://local/background",
        )
        val first = settings.limestoneAppearance()
        val second = settings.limestoneAppearance()
        assertEquals(first, second)
        assertEquals(
            LimestoneAppearance(
                theme = SettingsThemeMode.Light,
                accent = AppAccent.Harbour,
                background = AppBackground.None,
                tabBar = TabBarStyle.Solid,
            ),
            first,
        )
    }
}
