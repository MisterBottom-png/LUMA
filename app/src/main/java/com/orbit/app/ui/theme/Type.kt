package com.orbit.app.ui.theme

import androidx.compose.material3.Typography
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

val CalendarTypography = CalendarTypographyTokens(
    monthHeader = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    dateContext = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    modeLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    weekday = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    monthDate = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    timelineHour = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
    emptyState = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
)

val HomeTypography = HomeTypographyTokens(
    greeting = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    userName = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.3).sp,
    ),
    calendarMonth = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    calendarWeek = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    calendarWeekday = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    calendarDate = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = "tnum",
    ),
    capturePlaceholder = TextStyle(
        fontFamily = FontFamily.SansSerif,
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
private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val OrbitTypography = Typography(
    displayLarge = style(FontWeight.SemiBold, 36, 44, -0.6),
    displayMedium = style(FontWeight.SemiBold, 36, 44, -0.6),
    displaySmall = style(FontWeight.SemiBold, 36, 44, -0.6),
    headlineLarge = style(FontWeight.SemiBold, 30, 38, -0.5),
    // Screen titles on the main tabs.
    headlineMedium = style(FontWeight.SemiBold, 28, 34, -0.45),
    headlineSmall = style(FontWeight.SemiBold, 22, 28, -0.25),
    titleLarge = style(FontWeight.SemiBold, 20, 26, -0.2),
    titleMedium = style(FontWeight.SemiBold, 17, 23, -0.1),
    titleSmall = style(FontWeight.Medium, 15, 20, 0.0),
    bodyLarge = style(FontWeight.Normal, 17, 25, 0.0),
    bodyMedium = style(FontWeight.Normal, 15, 22, 0.0),
    bodySmall = style(FontWeight.Normal, 13, 18, 0.1),
    labelLarge = style(FontWeight.SemiBold, 14, 18, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(FontWeight.Medium, 11, 14, 0.3),
)
