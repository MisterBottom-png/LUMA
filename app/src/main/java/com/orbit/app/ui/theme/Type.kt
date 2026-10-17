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
 * The Material type scale for existing screens, all in Noto Sans (proposal section 6.2). Screen title is
 * headlineMedium 24/32 semibold. Body is 15-16/23, metadata (bodySmall) 14/20, labels at least 12 sp,
 * nothing important below 13 sp. No light weights, no all-caps, modest letter spacing.
 */
private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal fun orbitTypography(family: FontFamily) = Typography(
    displayLarge = style(family, FontWeight.SemiBold, 36, 44),
    displayMedium = style(family, FontWeight.SemiBold, 32, 40),
    displaySmall = style(family, FontWeight.SemiBold, 28, 36),
    headlineLarge = style(family, FontWeight.SemiBold, 28, 36),
    // Screen titles (section 6.2): 24/32 semibold.
    headlineMedium = style(family, FontWeight.SemiBold, 24, 32),
    headlineSmall = style(family, FontWeight.SemiBold, 20, 28),
    titleLarge = style(family, FontWeight.SemiBold, 20, 28),
    titleMedium = style(family, FontWeight.SemiBold, 16, 23),
    // Body and controls (section 6.2): 15/23 semibold.
    titleSmall = style(family, FontWeight.SemiBold, 15, 23),
    bodyLarge = style(family, FontWeight.Normal, 16, 23),
    bodyMedium = style(family, FontWeight.Normal, 15, 23),
    // Metadata: 14/20.
    bodySmall = style(family, FontWeight.Normal, 14, 20),
    labelLarge = style(family, FontWeight.SemiBold, 15, 23),
    labelMedium = style(family, FontWeight.Medium, 13, 18, 0.1),
    // Tab labels: 12 sp minimum, grows with the font scale.
    labelSmall = style(family, FontWeight.SemiBold, 12, 16, 0.1),
)

/**
 * Noto Sans, the app's voice (SIL OFL 1.1, licence in assets/licenses). It covers Latin, Estonian and
 * Cyrillic, so Russian uses the same family as English. Only three static weights are bundled: a Bold
 * request resolves to the semibold file.
 */
internal val TalleleSans = FontFamily(
    Font(R.font.noto_sans_regular, FontWeight.Normal),
    Font(R.font.noto_sans_medium, FontWeight.Medium),
    Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_semibold, FontWeight.Bold),
)

/**
 * Literata, the person's own words (SIL OFL 1.1, licence in assets/licenses). Regular and medium are
 * bundled: SemiBold and Bold requests resolve to medium.
 */
internal val TalleleSerif = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_medium, FontWeight.Medium),
    Font(R.font.literata_medium, FontWeight.SemiBold),
    Font(R.font.literata_medium, FontWeight.Bold),
)

/** Every language uses the same family; the old Russian system-font special case is gone. */
@Suppress("UNUSED_PARAMETER")
internal fun orbitFontFamilyFor(language: String): FontFamily = TalleleSans

internal val OrbitMaterialTypography: Typography = orbitTypography(TalleleSans)

val LocalOrbitFontFamily = staticCompositionLocalOf<FontFamily> { TalleleSans }

val HomeTypography: HomeTypographyTokens
    @Composable @ReadOnlyComposable get() = homeTypography(LocalOrbitFontFamily.current)

val CalendarTypography: CalendarTypographyTokens
    @Composable @ReadOnlyComposable get() = calendarTypography(LocalOrbitFontFamily.current)

/**
 * The two voices of the Limestone type system (section 6.2). Sizes are sp so they grow with the font
 * scale; rows must grow with them and never clip.
 */
@Immutable
data class TalleleTypography(
    /** Noto Sans 24/32 600. */
    val screenTitle: TextStyle,
    /** Literata 24/34 500. */
    val itemTitle: TextStyle,
    /** Literata 20/30 400. */
    val captureText: TextStyle,
    /** Literata 17/26 400: the person's words in lists. */
    val listWords: TextStyle,
    /** Noto Sans 16/23 400. */
    val body: TextStyle,
    /** Noto Sans 16/23 600. */
    val bodyStrong: TextStyle,
    /** Noto Sans 15/23 600: buttons and controls. */
    val control: TextStyle,
    /** Noto Sans 14/20 400. */
    val metadata: TextStyle,
    /** Noto Sans 12/16 600; grows with the font scale. */
    val tabLabel: TextStyle,
    /** [metadata] with tabular numbers so times line up. */
    val timeTabular: TextStyle,
)

private fun voice(family: FontFamily, weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

internal fun talleleTypography(sans: FontFamily = TalleleSans, serif: FontFamily = TalleleSerif): TalleleTypography {
    val metadata = voice(sans, FontWeight.Normal, 14, 20)
    return TalleleTypography(
        screenTitle = voice(sans, FontWeight.SemiBold, 24, 32),
        itemTitle = voice(serif, FontWeight.Medium, 24, 34),
        captureText = voice(serif, FontWeight.Normal, 20, 30),
        listWords = voice(serif, FontWeight.Normal, 17, 26),
        body = voice(sans, FontWeight.Normal, 16, 23),
        bodyStrong = voice(sans, FontWeight.SemiBold, 16, 23),
        control = voice(sans, FontWeight.SemiBold, 15, 23),
        metadata = metadata,
        tabLabel = voice(sans, FontWeight.SemiBold, 12, 16),
        timeTabular = metadata.copy(fontFeatureSettings = "tnum"),
    )
}

internal val TalleleTypographyDefault: TalleleTypography = talleleTypography()

val LocalTalleleTypography = staticCompositionLocalOf { TalleleTypographyDefault }
