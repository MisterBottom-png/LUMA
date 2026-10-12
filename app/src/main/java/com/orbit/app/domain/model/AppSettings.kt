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

/**
 * How much glass LUMA draws. Strong allows live blur where Android supports it,
 * Soft keeps translucent surfaces without live blur, Off uses solid surfaces.
 */
enum class GlassEffect { Strong, Soft, Off }

enum class AiMode(val label: String) {
    LocalOnly("Local only"),
    GeminiApi("Gemini API"),
}

object AiModelDefaults {
    const val FastModelId = "gemini-3.1-flash-lite"
    const val ReasoningModelId = "gemini-3.5-flash"
}

object GeminiConsent {
    /**
     * 2: the consent says plainly that Gemini suggestions are filed automatically with the
     * same rules as local ones, and names "Sort right after saving" as the pause (review F11).
     * Consent given to version 1 no longer counts, so it is asked again.
     */
    const val CurrentVersion = 2
}

/** Stored until the person sets a name in Settings. */
const val PlaceholderUserName = "user"

/** The name the person set, or null while it is still the placeholder. */
fun AppSettings.chosenName(): String? = chosenUserName(userName)

fun chosenUserName(userName: String): String? =
    userName.trim().takeUnless { it.isEmpty() || it.equals(PlaceholderUserName, ignoreCase = true) }

data class AppSettings(
    val userName: String = PlaceholderUserName,
    val themeMode: SettingsThemeMode = SettingsThemeMode.Auto,
    val timeFormatMode: SettingsTimeFormatMode = SettingsTimeFormatMode.Device,
    /**
     * Violet Mist for new installs: glass needs colour behind it. Existing installs keep
     * the preset they have stored.
     */
    val backgroundPreset: BackgroundPreset = BackgroundPreset.VioletMist,
    val customBackgroundUri: String? = null,
    val backgroundBlur: BackgroundBlur = BackgroundBlur.Soft,
    val backgroundDim: Float = 0.12f,
    val backgroundDimmingMode: BackgroundDimmingMode = BackgroundDimmingMode.Adaptive,
    val glassPreference: GlassPreference = GlassPreference.Standard,
    val glassEffect: GlassEffect = GlassEffect.Strong,
    /** Tallele violet for new installs (review §11); earlier installs keep what they had (C7). */
    val accentColor: AppAccentColor = AppAccentColor.LumaViolet,
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
    val hasCompletedFirstTimeTutorial: Boolean = false,
    /** Opens the sorting sheet right after a capture is saved (the pre-redesign flow). */
    val sortRightAfterSaving: Boolean = false,
    /** Places the cursor in the Home capture box when Home opens. */
    val focusCaptureOnOpen: Boolean = false,
    /** The once-only notice that the phone's speech screen may use Google's servers. */
    val hasSeenVoiceCloudNotice: Boolean = false,
    /**
     * The thought the save receipt on Home is about. Kept here, not in the screen's saved
     * state, so the receipt is still right after a force-stop, a reboot or a removed task.
     */
    val lastReceiptCaptureId: Long? = null,
    /** True once the user swiped the receipt away or opened its item; the next save shows a new one. */
    val lastReceiptDismissed: Boolean = false,
    /**
     * The one-time tip under the first receipt has been read (the next save or closing that
     * receipt counts). Installs that finished the old guide count as having seen it.
     */
    val hasSeenFirstReceiptTip: Boolean = false,
    /** Home already asked Android once to show notifications, so a reminder from Home can ring. */
    val hasAskedForNotifications: Boolean = false,
)

/**
 * Gemini is chosen and this feature's switch is on. The one rule OrbitAiRouter uses for
 * every feature; a saved API key is also needed, and the router reads it when it sends.
 */
fun AppSettings.geminiOnFor(featureEnabled: Boolean): Boolean =
    // N047: without the current consent nothing goes to Gemini, on any path.
    aiMode == AiMode.GeminiApi && featureEnabled && hasCurrentGeminiConsent

/**
 * True exactly when a thought saved from Home would be sent to Gemini: Home's save runs
 * OrbitAiRouter.analyzeCapture, which uses Gemini for capture sorting or for a Brain Dump
 * when that switch is on and a key is saved. Without a key everything stays local.
 */
fun AppSettings.homeSavesUseGemini(hasGeminiKey: Boolean): Boolean =
    hasGeminiKey && (geminiOnFor(useGeminiForCapture) || geminiOnFor(useGeminiForBrainDump))

/**
 * Only Brain Dump sorting is on: the router sends a save only when it reads as a list of
 * thoughts, and a single thought stays on the phone.
 */
fun AppSettings.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey: Boolean): Boolean =
    homeSavesUseGemini(hasGeminiKey) && !geminiOnFor(useGeminiForCapture)

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
        glassEffect = defaults.glassEffect,
        accentColor = defaults.accentColor,
        paletteMode = defaults.paletteMode,
        textColor = defaults.textColor,
    )
}

/**
 * Learned rules are used only in Gemini prompts: with learning on, sharing with
 * Gemini on, and Gemini chosen. Local suggestions never read them.
 */
val AppSettings.learnedRulesReachGemini: Boolean
    get() = enableLocalAiLearning && shareLocalLearningWithGemini && aiMode == AiMode.GeminiApi && hasCurrentGeminiConsent
