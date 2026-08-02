package com.orbit.app.ui.screens.settings

import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.BackgroundBlur
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.GlassPreference
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.localization.AppLanguage

@StringRes
internal fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SystemDefault -> R.string.language_system_default
    AppLanguage.English -> R.string.language_english
    AppLanguage.Estonian -> R.string.language_estonian
    AppLanguage.Russian -> R.string.language_russian
}

@StringRes
internal fun SettingsThemeMode.labelRes(): Int = when (this) {
    SettingsThemeMode.Light -> R.string.theme_light
    SettingsThemeMode.Dark -> R.string.theme_dark
    SettingsThemeMode.Auto -> R.string.theme_auto
}

@StringRes
internal fun SettingsTimeFormatMode.labelRes(): Int = when (this) {
    SettingsTimeFormatMode.Device -> R.string.time_device_default
    SettingsTimeFormatMode.TwelveHour -> R.string.time_twelve_hour
    SettingsTimeFormatMode.TwentyFourHour -> R.string.time_twenty_four_hour
}

@StringRes
internal fun BackgroundPreset.labelRes(): Int = when (this) {
    BackgroundPreset.InkPaper -> R.string.background_ink_paper
    BackgroundPreset.SoftDawn -> R.string.background_soft_dawn
    BackgroundPreset.VioletMist -> R.string.background_violet_mist
    BackgroundPreset.CalmSky -> R.string.background_calm_sky
    BackgroundPreset.NightOrbit -> R.string.background_night_glow
}

@StringRes
internal fun AppAccentColor.labelRes(): Int = when (this) {
    AppAccentColor.InkPaper -> R.string.accent_ink_paper
    AppAccentColor.LumaViolet -> R.string.accent_luma_violet
    AppAccentColor.Sage -> R.string.accent_sage
    AppAccentColor.Rose -> R.string.accent_rose
    AppAccentColor.Amber -> R.string.accent_amber
    AppAccentColor.Ocean -> R.string.accent_ocean
}

@StringRes
internal fun AppTextColor.labelRes(): Int = when (this) {
    AppTextColor.Neutral -> R.string.text_neutral
    AppTextColor.Plum -> R.string.text_plum
    AppTextColor.Forest -> R.string.text_forest
    AppTextColor.WarmIvory -> R.string.text_warm_ivory
}

@StringRes
internal fun AiMode.labelRes(): Int = when (this) {
    AiMode.LocalOnly -> R.string.ai_local_only
    AiMode.GeminiApi -> R.string.ai_gemini_api
}

@StringRes
internal fun BackgroundBlur.labelRes(): Int = when (this) {
    BackgroundBlur.None -> R.string.settings_sharp
    BackgroundBlur.Soft -> R.string.settings_soft
    BackgroundBlur.Medium -> R.string.settings_blur_medium
    BackgroundBlur.Strong -> R.string.settings_blur_strong
}

@StringRes
internal fun GlassPreference.labelRes(): Int = when (this) {
    GlassPreference.Subtle -> R.string.settings_opacity_subtle
    GlassPreference.Standard -> R.string.settings_opacity_standard
    GlassPreference.Prominent -> R.string.settings_opacity_prominent
}
