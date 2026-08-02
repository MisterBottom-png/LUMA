package com.orbit.app.domain.model

enum class SettingsThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    Auto("Auto"),
}

enum class SettingsTimeFormatMode(val label: String) {
    Device("Device default"),
    TwelveHour("12-hour"),
    TwentyFourHour("24-hour"),
}

fun SettingsTimeFormatMode.uses24HourClock(deviceUses24HourClock: Boolean): Boolean = when (this) {
    SettingsTimeFormatMode.Device -> deviceUses24HourClock
    SettingsTimeFormatMode.TwelveHour -> false
    SettingsTimeFormatMode.TwentyFourHour -> true
}

enum class BackgroundPreset(val label: String) {
    InkPaper("Ink & Paper"),
    SoftDawn("Soft Dawn"),
    VioletMist("Violet Mist"),
    CalmSky("Calm Sky"),
    NightOrbit("Night Glow"),
}

enum class AppAccentColor(val label: String) {
    InkPaper("Ink & Paper"),
    LumaViolet("Luma violet"),
    Sage("Sage"),
    Rose("Rose"),
    Amber("Amber"),
    Ocean("Ocean"),
}

enum class AppTextColor(val label: String) {
    Neutral("Neutral"),
    Plum("Plum"),
    Forest("Forest"),
    WarmIvory("Warm ivory"),
}

enum class AppearancePaletteMode { Standard, FullPalette }

enum class BackgroundBlur(val label: String, val legacyStrength: Float) {
    None("None", 0f),
    Soft("Soft", 0.35f),
    Medium("Medium", 0.60f),
    Strong("Strong", 1f),
}

enum class GlassPreference(val label: String, val legacyStrength: Float) {
    Subtle("Subtle", 0.45f),
    Standard("Standard", 0.72f),
    Prominent("Prominent", 0.90f),
}

enum class BackgroundDimmingMode { Adaptive, Manual }

enum class AiMode(val label: String) {
    LocalOnly("Local only"),
    GeminiApi("Gemini API"),
}

object AiModelDefaults {
    const val FastModelId = "gemini-3.1-flash-lite"
    const val ReasoningModelId = "gemini-3.5-flash"
}

object GeminiConsent {
    const val CurrentVersion = 1
}

data class AppSettings(
    val userName: String = "user",
    val themeMode: SettingsThemeMode = SettingsThemeMode.Auto,
    val timeFormatMode: SettingsTimeFormatMode = SettingsTimeFormatMode.Device,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.InkPaper,
    val customBackgroundUri: String? = null,
    val backgroundBlur: BackgroundBlur = BackgroundBlur.Soft,
    val backgroundDim: Float = 0.12f,
    val backgroundDimmingMode: BackgroundDimmingMode = BackgroundDimmingMode.Adaptive,
    val glassPreference: GlassPreference = GlassPreference.Standard,
    val accentColor: AppAccentColor = AppAccentColor.InkPaper,
    val paletteMode: AppearancePaletteMode = AppearancePaletteMode.Standard,
    val textColor: AppTextColor = AppTextColor.Neutral,
    val staleLoopDays: Int = 7,
    val aiMode: AiMode = AiMode.LocalOnly,
    val geminiConsentVersion: Int = 0,
    val enableLocalAiLearning: Boolean = false,
    val shareLocalLearningWithGemini: Boolean = false,
    val geminiFastModelId: String = AiModelDefaults.FastModelId,
    val geminiReasoningModelId: String = AiModelDefaults.ReasoningModelId,
    val useGeminiForCapture: Boolean = false,
    val useGeminiForMakeSmaller: Boolean = false,
    val useGeminiForBrainDump: Boolean = false,
    val useGeminiForSituation: Boolean = false,
    val useGeminiForReview: Boolean = false,
)

val AppSettings.hasCurrentGeminiConsent: Boolean
    get() = geminiConsentVersion >= GeminiConsent.CurrentVersion

fun AppSettings.withDefaultAppearance(): AppSettings {
    val defaults = AppSettings()
    return copy(
        themeMode = defaults.themeMode,
        backgroundPreset = defaults.backgroundPreset,
        customBackgroundUri = defaults.customBackgroundUri,
        backgroundBlur = defaults.backgroundBlur,
        backgroundDim = defaults.backgroundDim,
        backgroundDimmingMode = defaults.backgroundDimmingMode,
        glassPreference = defaults.glassPreference,
        accentColor = defaults.accentColor,
        paletteMode = defaults.paletteMode,
        textColor = defaults.textColor,
    )
}
