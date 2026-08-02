package com.orbit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.AppearancePaletteMode
import com.orbit.app.domain.model.SettingsThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5962), onPrimary = Color.White,
    primaryContainer = Color(0xFFC1DDE4), onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF705D4A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFADDBD), onSecondaryContainer = Color(0xFF2A1707),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFCFAF5), onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE2E3DD), onSurfaceVariant = Color(0xFF434842),
    surfaceContainerLowest = Color(0xFFFFFCF8), surfaceContainerLow = Color(0xFFF6F4EE),
    surfaceContainer = Color(0xFFF0EFE9), surfaceContainerHigh = Color(0xFFEAE9E3),
    surfaceContainerHighest = Color(0xFFE4E3DD),
    outline = Color(0xFF737770), outlineVariant = Color(0xFFC3C7C0),
    surfaceTint = Color(0xFF3D5962), inverseSurface = Color(0xFF2F312E), inverseOnSurface = Color(0xFFF1F1EB),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5CAD3), onPrimary = Color(0xFF07363F),
    primaryContainer = Color(0xFF254B54), onPrimaryContainer = Color(0xFFC1E6EF),
    secondary = Color(0xFFDEC3A8), onSecondary = Color(0xFF3E2D1D),
    secondaryContainer = Color(0xFF574331), onSecondaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF121412), onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF191C1A), onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF434842), onSurfaceVariant = Color(0xFFC3C8C1),
    surfaceContainerLowest = Color(0xFF0D0F0E), surfaceContainerLow = Color(0xFF171A18),
    surfaceContainer = Color(0xFF1C1F1D), surfaceContainerHigh = Color(0xFF272A28),
    surfaceContainerHighest = Color(0xFF323532),
    outline = Color(0xFF8D928A), outlineVariant = Color(0xFF434842),
    surfaceTint = Color(0xFFA5CAD3), inverseSurface = Color(0xFFE2E3DD), inverseOnSurface = Color(0xFF2F312E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

internal fun defaultOrbitColorScheme(isDark: Boolean): ColorScheme = if (isDark) DarkColors else LightColors

data class OrbitSemanticColors(
    val success: Color, val warning: Color, val info: Color, val ai: Color, val needsReview: Color, val archived: Color,
)

val LightSemanticColors = OrbitSemanticColors(
    success = Color(0xFF3E6B49), warning = Color(0xFF835A00), info = Color(0xFF365D73),
    ai = Color(0xFF3D5962), needsReview = Color(0xFF8B4A36), archived = Color(0xFF626862),
)
val DarkSemanticColors = OrbitSemanticColors(
    success = Color(0xFF9CCAA2), warning = Color(0xFFFFC869), info = Color(0xFF9BCBEB),
    ai = Color(0xFFA5CAD3), needsReview = Color(0xFFFFB5A0), archived = Color(0xFFC3C8C1),
)

@Composable
fun OrbitTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val useDarkColors = when (settings.themeMode) {
        SettingsThemeMode.Light -> false
        SettingsThemeMode.Dark -> true
        SettingsThemeMode.Auto -> isSystemInDarkTheme()
    }
    val base = if (useDarkColors) DarkColors else LightColors
    MaterialTheme(
        colorScheme = base.withPersonalColors(settings.accentColor, settings.textColor, settings.paletteMode, useDarkColors),
        typography = OrbitTypography,
        content = content,
    )
}

private fun ColorScheme.withPersonalColors(
    accentColor: AppAccentColor,
    textColor: AppTextColor,
    paletteMode: AppearancePaletteMode,
    isDark: Boolean,
): ColorScheme {
    val accent = accentPalette(accentColor, isDark)
    val text = textPalette(textColor, isDark, onSurface)
    return copy(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer, onPrimaryContainer = accent.onPrimaryContainer,
        secondary = if (paletteMode == AppearancePaletteMode.FullPalette) accent.secondary else secondary,
        onSecondary = if (paletteMode == AppearancePaletteMode.FullPalette) accent.onSecondary else onSecondary,
        secondaryContainer = if (paletteMode == AppearancePaletteMode.FullPalette) accent.secondaryContainer else secondaryContainer,
        onSecondaryContainer = if (paletteMode == AppearancePaletteMode.FullPalette) accent.onSecondaryContainer else onSecondaryContainer,
        onBackground = text.primary, onSurface = text.primary, onSurfaceVariant = text.secondary,
    )
}

private data class AccentPalette(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
)

internal data class TextPalette(val primary: Color, val secondary: Color)

private fun accentPalette(accent: AppAccentColor, dark: Boolean): AccentPalette {
    val light = when (accent) {
        AppAccentColor.InkPaper -> AccentPalette(Color(0xFF3D5962), Color.White, Color(0xFFC1DDE4), Color(0xFF001F26), Color(0xFF705D4A), Color.White, Color(0xFFFADDBD), Color(0xFF2A1707))
        AppAccentColor.LumaViolet -> AccentPalette(Color(0xFF6550C8), Color.White, Color(0xFFE8DFFF), Color(0xFF25145E), Color(0xFF3F7479), Color.White, Color(0xFFC5ECEF), Color(0xFF082F33))
        AppAccentColor.Sage -> AccentPalette(Color(0xFF3E6F45), Color.White, Color(0xFFD9F2D5), Color(0xFF103117), Color(0xFF74642F), Color.White, Color(0xFFF1E5BB), Color(0xFF2D2508))
        AppAccentColor.Rose -> AccentPalette(Color(0xFF99415E), Color.White, Color(0xFFFFD9E2), Color(0xFF3D061B), Color(0xFF725A42), Color.White, Color(0xFFFBDDBF), Color(0xFF2A1707))
        AppAccentColor.Amber -> AccentPalette(Color(0xFF865400), Color.White, Color(0xFFFFDFA5), Color(0xFF2B1700), Color(0xFF5D6F47), Color.White, Color(0xFFE0EBC8), Color(0xFF182308))
        AppAccentColor.Ocean -> AccentPalette(Color(0xFF2D6684), Color.White, Color(0xFFCDEBFF), Color(0xFF001E2E), Color(0xFF5C6090), Color.White, Color(0xFFE0E0FF), Color(0xFF181A49))
    }
    if (!dark) return light
    return when (accent) {
        AppAccentColor.InkPaper -> AccentPalette(Color(0xFFA5CAD3), Color(0xFF07363F), Color(0xFF254B54), Color(0xFFC1E6EF), Color(0xFFDEC3A8), Color(0xFF3E2D1D), Color(0xFF574331), Color(0xFFFFDCC0))
        else -> light.copy(primary = light.primaryContainer, onPrimary = light.onPrimaryContainer, primaryContainer = light.primary.copy(alpha = 0.75f), onPrimaryContainer = light.primaryContainer, secondary = light.secondaryContainer, onSecondary = light.onSecondaryContainer, secondaryContainer = light.secondary.copy(alpha = 0.72f), onSecondaryContainer = light.secondaryContainer)
    }
}

internal fun textPalette(textColor: AppTextColor, isDark: Boolean, defaultPrimary: Color): TextPalette = when (textColor) {
    AppTextColor.Neutral -> TextPalette(defaultPrimary, if (isDark) Color(0xFFC3C8C1) else Color(0xFF434842))
    AppTextColor.Plum -> TextPalette(if (isDark) Color(0xFFF1E5FF) else Color(0xFF2A173C), if (isDark) Color(0xFFD4C6E7) else Color(0xFF55445F))
    AppTextColor.Forest -> TextPalette(if (isDark) Color(0xFFE6F1E5) else Color(0xFF152A1D), if (isDark) Color(0xFFC8D8C5) else Color(0xFF3E4F41))
    AppTextColor.WarmIvory -> TextPalette(if (isDark) Color(0xFFFFF1DB) else Color(0xFF241B12), if (isDark) Color(0xFFE0D1BD) else Color(0xFF493D30))
}
