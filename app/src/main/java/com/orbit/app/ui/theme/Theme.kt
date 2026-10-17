package com.orbit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.model.AppAccent
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.limestoneAppearance

/** The default (Ink) Material scheme for a theme. Used by tests and previews; the app uses [OrbitTheme]. */
internal fun defaultOrbitColorScheme(isDark: Boolean): ColorScheme =
    talleleColors(isDark, AppAccent.Ink).toMaterialColorScheme()

/**
 * Limestone Harbour theme. It renders from [AppSettings.limestoneAppearance] (theme and accent);
 * the old text colour, second colour and tinted-surface settings no longer influence it.
 *
 * Provides the Material colour scheme, typography and shapes (so existing screens pick up the new look),
 * plus [TalleleTheme.colors] and [TalleleTheme.typography] for new code.
 */
@Composable
fun OrbitTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val appearance = settings.limestoneAppearance()
    val isDark = when (appearance.theme) {
        SettingsThemeMode.Light -> false
        SettingsThemeMode.Dark -> true
        SettingsThemeMode.Auto -> isSystemInDarkTheme()
    }
    val colors = remember(isDark, appearance.accent) { talleleColors(isDark, appearance.accent) }
    val scheme = remember(colors) { colors.toMaterialColorScheme() }
    CompositionLocalProvider(
        LocalTalleleColors provides colors,
        LocalTalleleTypography provides TalleleTypographyDefault,
        LocalOrbitFontFamily provides TalleleSans,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OrbitMaterialTypography,
            shapes = TalleleMaterialShapes,
            content = content,
        )
    }
}

/** Access to the Limestone tokens: `TalleleTheme.colors.ink`, `TalleleTheme.typography.listWords`. */
object TalleleTheme {
    val colors: TalleleColors
        @Composable @ReadOnlyComposable get() = LocalTalleleColors.current
    val typography: TalleleTypography
        @Composable @ReadOnlyComposable get() = LocalTalleleTypography.current
}

internal data class TextPalette(val primary: Color, val secondary: Color)

/**
 * Old Appearance screen only, removed in P5. The theme no longer uses it; it only paints the swatches
 * on the old text colour picker.
 */
internal fun textPalette(textColor: AppTextColor, isDark: Boolean, defaultPrimary: Color): TextPalette = when (textColor) {
    AppTextColor.Neutral -> TextPalette(defaultPrimary, if (isDark) Color(0xFFC3C8C1) else Color(0xFF434842))
    AppTextColor.Plum -> TextPalette(if (isDark) Color(0xFFF1E5FF) else Color(0xFF2A173C), if (isDark) Color(0xFFD4C6E7) else Color(0xFF55445F))
    AppTextColor.Forest -> TextPalette(if (isDark) Color(0xFFE6F1E5) else Color(0xFF152A1D), if (isDark) Color(0xFFC8D8C5) else Color(0xFF3E4F41))
    AppTextColor.WarmIvory -> TextPalette(if (isDark) Color(0xFFFFF1DB) else Color(0xFF241B12), if (isDark) Color(0xFFE0D1BD) else Color(0xFF493D30))
}
