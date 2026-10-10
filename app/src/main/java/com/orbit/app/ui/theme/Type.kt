package com.orbit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import com.orbit.app.R
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class HomeTypographyTokens(
    val greeting: TextStyle,
    val userName: TextStyle,
    val calendarMonth: TextStyle,
    val calendarWeek: TextStyle,
    val calendarWeekday: TextStyle,
    val calendarDate: TextStyle,
    val capturePlaceholder: TextStyle,
)

@Immutable
data class CalendarTypographyTokens(
    val monthHeader: TextStyle,
    val dateContext: TextStyle,
    val modeLabel: TextStyle,
    val weekday: TextStyle,
    val monthDate: TextStyle,
    val timelineHour: TextStyle,
    val emptyState: TextStyle,
)

internal fun calendarTypography(family: FontFamily) = CalendarTypographyTokens(
    monthHeader = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    dateContext = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    modeLabel = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    weekday = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    monthDate = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    timelineHour = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
    emptyState = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
)

internal fun homeTypography(family: FontFamily) = HomeTypographyTokens(
    greeting = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    userName = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.3).sp,
    ),
    // The week on Home is a quiet line under the greeting, not a second heading.
    calendarMonth = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    calendarWeek = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    calendarWeekday = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    calendarDate = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum",
    ),
    capturePlaceholder = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
)

/**
 * One type scale for the whole app. Few sizes, two weights (Normal for reading, SemiBold for
 * titles; Medium only for small labels). Large type is tightened, small type opened slightly,
 * as a premium type system does.
 */
private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal fun orbitTypography(family: FontFamily) = Typography(
    displayLarge = style(family, FontWeight.SemiBold, 36, 44, -0.6),
    displayMedium = style(family, FontWeight.SemiBold, 36, 44, -0.6),
    displaySmall = style(family, FontWeight.SemiBold, 36, 44, -0.6),
    headlineLarge = style(family, FontWeight.SemiBold, 30, 38, -0.5),
    // Screen titles on the main tabs.
    headlineMedium = style(family, FontWeight.SemiBold, 28, 34, -0.45),
    headlineSmall = style(family, FontWeight.SemiBold, 22, 28, -0.25),
    titleLarge = style(family, FontWeight.SemiBold, 20, 26, -0.2),
    titleMedium = style(family, FontWeight.SemiBold, 17, 23, -0.1),
    titleSmall = style(family, FontWeight.Medium, 15, 20, 0.0),
    bodyLarge = style(family, FontWeight.Normal, 17, 25, 0.0),
    bodyMedium = style(family, FontWeight.Normal, 15, 22, 0.0),
    bodySmall = style(family, FontWeight.Normal, 13, 18, 0.1),
    labelLarge = style(family, FontWeight.SemiBold, 14, 18, 0.1),
    labelMedium = style(family, FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(family, FontWeight.Medium, 11, 14, 0.3),
)

/**
 * Tallele's typeface: Google Sans Flex (SIL OFL 1.1, licence in assets/licenses), bundled as
 * three small static Latin instances. It has no Cyrillic, so Russian uses the system font
 * throughout instead of mixing two typefaces in one line.
 */
internal val TalleleSans = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_semibold, FontWeight.Bold),
)

internal fun orbitFontFamilyFor(language: String): FontFamily =
    if (language.equals("ru", ignoreCase = true)) FontFamily.SansSerif else TalleleSans

val LocalOrbitFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

val HomeTypography: HomeTypographyTokens
    @Composable @ReadOnlyComposable get() = homeTypography(LocalOrbitFontFamily.current)

val CalendarTypography: CalendarTypographyTokens
    @Composable @ReadOnlyComposable get() = calendarTypography(LocalOrbitFontFamily.current)
