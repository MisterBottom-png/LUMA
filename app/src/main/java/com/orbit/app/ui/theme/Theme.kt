package com.orbit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.SettingsThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF6550C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DFFF),
    onPrimaryContainer = Color(0xFF25145E),
    secondary = Color(0xFF3F7479),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5ECEF),
    onSecondaryContainer = Color(0xFF082F33),
    background = Color(0xFFF7F3FB),
    onBackground = Color(0xFF211D27),
    surface = Color(0xFFF9F5FD),
    onSurface = Color(0xFF211D27),
    surfaceVariant = Color(0xFFE8E1EC),
    onSurfaceVariant = Color(0xFF4A454E),
    outline = Color(0xFF7B747E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDBDFF),
    onPrimary = Color(0xFF362176),
    primaryContainer = Color(0xFF4D3992),
    onPrimaryContainer = Color(0xFFE8DFFF),
    secondary = Color(0xFFA9CED1),
    onSecondary = Color(0xFF12373B),
    secondaryContainer = Color(0xFF294F53),
    onSecondaryContainer = Color(0xFFC5ECEF),
    background = Color(0xFF15121B),
    onBackground = Color(0xFFEAE3ED),
    surface = Color(0xFF19161F),
    onSurface = Color(0xFFEAE3ED),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCCC4CF),
    outline = Color(0xFF958E98),
)

@Composable
fun OrbitTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val useDarkColors = when (settings.themeMode) {
        SettingsThemeMode.Light -> false
        SettingsThemeMode.Dark -> true
        SettingsThemeMode.Auto -> isSystemInDarkTheme()
    }
    val baseColors = if (useDarkColors) DarkColors else LightColors

    MaterialTheme(
        colorScheme = baseColors.withPersonalColors(
            accentColor = settings.accentColor,
            textColor = settings.textColor,
            isDark = useDarkColors,
        ),
        typography = OrbitTypography,
        content = content,
    )
}

private fun androidx.compose.material3.ColorScheme.withPersonalColors(
    accentColor: AppAccentColor,
    textColor: AppTextColor,
    isDark: Boolean,
): androidx.compose.material3.ColorScheme {
    val accent = accentPalette(accentColor, isDark)
    val text = textPalette(textColor, isDark, defaultPrimary = onSurface)
    return copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        onBackground = text.primary,
        onSurface = text.primary,
        onSurfaceVariant = text.secondary,
    )
}

private data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

internal data class TextPalette(
    val primary: Color,
    val secondary: Color,
)

private fun accentPalette(
    accentColor: AppAccentColor,
    isDark: Boolean,
): AccentPalette = when (accentColor) {
    AppAccentColor.LumaViolet -> if (isDark) {
        AccentPalette(
            primary = Color(0xFFCDBDFF),
            onPrimary = Color(0xFF362176),
            primaryContainer = Color(0xFF4D3992),
            onPrimaryContainer = Color(0xFFE8DFFF),
            secondary = Color(0xFFA9CED1),
            onSecondary = Color(0xFF12373B),
            secondaryContainer = Color(0xFF294F53),
            onSecondaryContainer = Color(0xFFC5ECEF),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF6550C8),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE8DFFF),
            onPrimaryContainer = Color(0xFF25145E),
            secondary = Color(0xFF3F7479),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFC5ECEF),
            onSecondaryContainer = Color(0xFF082F33),
        )
    }

    AppAccentColor.Sage -> if (isDark) {
        AccentPalette(
            primary = Color(0xFFB6D9B8),
            onPrimary = Color(0xFF18351D),
            primaryContainer = Color(0xFF2F5133),
            onPrimaryContainer = Color(0xFFD2F1D0),
            secondary = Color(0xFFCFC5A4),
            onSecondary = Color(0xFF352F12),
            secondaryContainer = Color(0xFF524A2D),
            onSecondaryContainer = Color(0xFFECE1BD),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF3E6F45),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F2D5),
            onPrimaryContainer = Color(0xFF103117),
            secondary = Color(0xFF74642F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF1E5BB),
            onSecondaryContainer = Color(0xFF2D2508),
        )
    }

    AppAccentColor.Rose -> if (isDark) {
        AccentPalette(
            primary = Color(0xFFFFB4C6),
            onPrimary = Color(0xFF5B1830),
            primaryContainer = Color(0xFF7C2E47),
            onPrimaryContainer = Color(0xFFFFD9E2),
            secondary = Color(0xFFD6C1A7),
            onSecondary = Color(0xFF3D2C1B),
            secondaryContainer = Color(0xFF564231),
            onSecondaryContainer = Color(0xFFF2DDC2),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF99415E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9E2),
            onPrimaryContainer = Color(0xFF3D061B),
            secondary = Color(0xFF725A42),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFBDDBF),
            onSecondaryContainer = Color(0xFF2A1707),
        )
    }

    AppAccentColor.Amber -> if (isDark) {
        AccentPalette(
            primary = Color(0xFFFFC46B),
            onPrimary = Color(0xFF4A2B00),
            primaryContainer = Color(0xFF6B4100),
            onPrimaryContainer = Color(0xFFFFDFA5),
            secondary = Color(0xFFBFD4BE),
            onSecondary = Color(0xFF293528),
            secondaryContainer = Color(0xFF3D503C),
            onSecondaryContainer = Color(0xFFDBF0D9),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF865400),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDFA5),
            onPrimaryContainer = Color(0xFF2B1700),
            secondary = Color(0xFF5D6F47),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE0EBC8),
            onSecondaryContainer = Color(0xFF182308),
        )
    }

    AppAccentColor.Ocean -> if (isDark) {
        AccentPalette(
            primary = Color(0xFFA4D5F3),
            onPrimary = Color(0xFF06344C),
            primaryContainer = Color(0xFF22506B),
            onPrimaryContainer = Color(0xFFCDEBFF),
            secondary = Color(0xFFBFC7E9),
            onSecondary = Color(0xFF2E3454),
            secondaryContainer = Color(0xFF454B6C),
            onSecondaryContainer = Color(0xFFDDE3FF),
        )
    } else {
        AccentPalette(
            primary = Color(0xFF2D6684),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCDEBFF),
            onPrimaryContainer = Color(0xFF001E2E),
            secondary = Color(0xFF5C6090),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE0E0FF),
            onSecondaryContainer = Color(0xFF181A49),
        )
    }
}

internal fun textPalette(
    textColor: AppTextColor,
    isDark: Boolean,
    defaultPrimary: Color,
): TextPalette = when (textColor) {
    AppTextColor.Default -> TextPalette(
        primary = defaultPrimary,
        secondary = if (isDark) Color(0xFFCCC4CF) else Color(0xFF4A454E),
    )

    AppTextColor.Ink -> TextPalette(
        primary = if (isDark) Color(0xFFF3EDF7) else Color(0xFF17141B),
        secondary = if (isDark) Color(0xFFD1CAD8) else Color(0xFF4B4650),
    )

    AppTextColor.Plum -> TextPalette(
        primary = if (isDark) Color(0xFFF1E5FF) else Color(0xFF2A173C),
        secondary = if (isDark) Color(0xFFD4C6E7) else Color(0xFF55445F),
    )

    AppTextColor.Forest -> TextPalette(
        primary = if (isDark) Color(0xFFE6F1E5) else Color(0xFF152A1D),
        secondary = if (isDark) Color(0xFFC8D8C5) else Color(0xFF3E4F41),
    )

    AppTextColor.WarmIvory -> TextPalette(
        primary = if (isDark) Color(0xFFFFF1DB) else Color(0xFF241B12),
        secondary = if (isDark) Color(0xFFE0D1BD) else Color(0xFF493D30),
    )
}
