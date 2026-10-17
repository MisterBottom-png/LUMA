package com.orbit.app.domain.model

/** The four checked accents (proposal v1.3 section 7.17). Ink is the default. */
enum class AppAccent { Ink, Harbour, Moss, Plum }

/** Built-in Capture backgrounds (section 7.17). None (plain limestone paper) is the default. */
enum class AppBackground { None, Limestone, HarbourMist, InkWash, PineFog, LinenLight }

/** Tab bar look the person chose (section 6.4). Glass is the default; the UI may fall back further at runtime. */
enum class TabBarStyle { Glass, Frosted, Solid }

/**
 * The Limestone appearance model (section 7.17): theme, one of four accents, one built-in
 * background and the tab bar style.
 *
 * In P1 this is derived at read time from the old settings with [AppSettings.limestoneAppearance];
 * nothing new is stored. P5 persists the result once, using the same mapping functions.
 */
data class LimestoneAppearance(
    /** Auto means "System". */
    val theme: SettingsThemeMode = SettingsThemeMode.Auto,
    val accent: AppAccent = AppAccent.Ink,
    val background: AppBackground = AppBackground.None,
    val tabBar: TabBarStyle = TabBarStyle.Glass,
)

/**
 * Section 7.17 accent conversion: Ink & Paper and Amber become Ink, Ocean becomes Harbour,
 * Sage becomes Moss, Luma violet and Rose become Plum.
 */
fun AppAccentColor.toAppAccent(): AppAccent = when (this) {
    AppAccentColor.InkPaper -> AppAccent.Ink
    AppAccentColor.Amber -> AppAccent.Ink
    AppAccentColor.Ocean -> AppAccent.Harbour
    AppAccentColor.Sage -> AppAccent.Moss
    AppAccentColor.LumaViolet -> AppAccent.Plum
    AppAccentColor.Rose -> AppAccent.Plum
}

/** Section 7.17 glass conversion: Strong becomes Glass, Soft becomes Frosted, Off becomes Solid. */
fun GlassEffect.toTabBarStyle(): TabBarStyle = when (this) {
    GlassEffect.Strong -> TabBarStyle.Glass
    GlassEffect.Soft -> TabBarStyle.Frosted
    GlassEffect.Off -> TabBarStyle.Solid
}

/**
 * Section 7.17 background conversion: every gradient preset and any own photo become
 * [AppBackground.None]. The own photo is not deleted here; it is only not used by the new model.
 */
@Suppress("UNUSED_PARAMETER")
fun appBackgroundFor(preset: BackgroundPreset, customBackgroundUri: String?): AppBackground = when (preset) {
    BackgroundPreset.InkPaper,
    BackgroundPreset.SoftDawn,
    BackgroundPreset.VioletMist,
    BackgroundPreset.CalmSky,
    BackgroundPreset.NightOrbit,
    -> AppBackground.None
}

/**
 * Pure, total conversion of the old appearance settings (section 7.17). Theme passes through
 * unchanged. Text colour, second colour (paletteMode), blur, dim, dimming mode and
 * glassPreference are dropped: they do not influence the result.
 */
fun AppSettings.limestoneAppearance(): LimestoneAppearance = LimestoneAppearance(
    theme = themeMode,
    accent = accentColor.toAppAccent(),
    background = appBackgroundFor(backgroundPreset, customBackgroundUri),
    tabBar = glassEffect.toTabBarStyle(),
)
