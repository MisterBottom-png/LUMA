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
    SoftDawn("Soft Dawn"),
    VioletMist("Violet Mist"),
    CalmSky("Calm Sky"),
    NightOrbit("Night Glow"),
}

enum class AppAccentColor(val label: String) {
    LumaViolet("Luma violet"),
    Sage("Sage"),
    Rose("Rose"),
    Amber("Amber"),
    Ocean("Ocean"),
}

enum class AppTextColor(val label: String) {
    Default("Default"),
    Ink("Ink"),
    Plum("Plum"),
    Forest("Forest"),
    WarmIvory("Warm ivory"),
}

enum class AiMode(val label: String) {
    LocalOnly("Local only"),
    GeminiApi("Gemini API"),
}

object AiModelDefaults {
    const val FastModelId = "gemini-3.1-flash-lite"
    const val ReasoningModelId = "gemini-3.5-flash"
}

data class AppSettings(
    val userName: String = "user",
    val themeMode: SettingsThemeMode = SettingsThemeMode.Auto,
    val timeFormatMode: SettingsTimeFormatMode = SettingsTimeFormatMode.Device,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.SoftDawn,
    val customBackgroundUri: String? = null,
    val backgroundBlur: Float = 0.35f,
    val backgroundDim: Float = 0.12f,
    val glassStrength: Float = 0.72f,
    val accentColor: AppAccentColor = AppAccentColor.LumaViolet,
    val textColor: AppTextColor = AppTextColor.Default,
    val staleLoopDays: Int = 7,
    val aiMode: AiMode = AiMode.LocalOnly,
    val geminiFastModelId: String = AiModelDefaults.FastModelId,
    val geminiReasoningModelId: String = AiModelDefaults.ReasoningModelId,
    val useGeminiForCapture: Boolean = false,
    val useGeminiForMakeSmaller: Boolean = false,
    val useGeminiForBrainDump: Boolean = false,
    val useGeminiForSituation: Boolean = false,
    val useGeminiForReview: Boolean = false,
)
