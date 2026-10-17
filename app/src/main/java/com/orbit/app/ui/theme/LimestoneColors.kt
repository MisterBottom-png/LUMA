package com.orbit.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.model.AppAccent

/**
 * The Limestone Harbour colour tokens (proposal v1.3 section 6.1 and the section 7.17 accent table).
 *
 * Each token has one job. Ink appears only on Keep or the main button, the selected tab, links and
 * the focus ring. Ochre appears only where a choice is needed. Error appears only for failures and
 * erasing. No token is a gradient, and every surface token is opaque and solid.
 */
@Immutable
data class TalleleColors(
    val isDark: Boolean,
    val accent: AppAccent,
    /** Screen background. */
    val page: Color,
    /** Lists, cards, capture box. */
    val surface: Color,
    /** Bottom sheets, dialogs, menus. */
    val sheet: Color,
    /** Search field, inputs. */
    val well: Color,
    val text: Color,
    /** Secondary text and icons. */
    val text2: Color,
    /** Placeholders. */
    val text3: Color,
    /** Decorative dividers only (not a UI boundary). */
    val hairline: Color,
    /** Edges of inputs, chips, checkboxes. */
    val outline: Color,
    /** Accent: Keep, selected tab, links, focus ring. */
    val ink: Color,
    val onInk: Color,
    /** Selected chip, selected tab pill. */
    val inkSoft: Color,
    val onInkSoft: Color,
    /** Signal colour, only when a choice is needed. */
    val ochre: Color,
    val ochreSoft: Color,
    val onOchreSoft: Color,
    val success: Color,
    val error: Color,
    /** Text or icon on a filled [error] button. */
    val onError: Color,
    val errorSoft: Color,
    /** Text on [errorSoft]. */
    val onErrorSoft: Color,
    /** The eight Space colours: a 10 dp square and an icon tint, always next to the Space name. */
    val spaces: SpaceColors,
)

/** The eight Space colours (section 6.1). Light values are the specified ones; dark values are derived. */
@Immutable
data class SpaceColors(
    val slate: Color,
    val moss: Color,
    val clay: Color,
    val sand: Color,
    val plum: Color,
    val harbour: Color,
    val rose: Color,
    val stone: Color,
) {
    fun all(): List<Color> = listOf(slate, moss, clay, sand, plum, harbour, rose, stone)
}

private val LightSpaces = SpaceColors(
    slate = Color(0xFF5B6B82), moss = Color(0xFF5A7A4E), clay = Color(0xFF9A5B44), sand = Color(0xFF7D6A4A),
    plum = Color(0xFF7A5582), harbour = Color(0xFF2F7178), rose = Color(0xFF9C4F62), stone = Color(0xFF6F6A60),
)

/** Derived: lighter tints of the same hues, at least 5.4:1 against the dark page, surface and sheet. */
private val DarkSpaces = SpaceColors(
    slate = Color(0xFF8EA0BA), moss = Color(0xFF8DAE80), clay = Color(0xFFD08F76), sand = Color(0xFFB8A27C),
    plum = Color(0xFFB791BF), harbour = Color(0xFF6FB0B7), rose = Color(0xFFD48499), stone = Color(0xFFA39E93),
)

/** The four accent roles that change with [AppAccent] (section 7.17). */
private data class InkSet(val ink: Color, val onInk: Color, val inkSoft: Color, val onInkSoft: Color)

private fun inkSet(accent: AppAccent, isDark: Boolean): InkSet = when (accent) {
    // Ink: every value is from section 6.1.
    AppAccent.Ink ->
        if (isDark) InkSet(Color(0xFFA8BFE8), Color(0xFF0F2147), Color(0xFF263A5E), Color(0xFFDCE6F8))
        else InkSet(Color(0xFF2D4B7E), Color(0xFFFFFFFF), Color(0xFFE0E7F3), Color(0xFF1A2D50))
    // The ink value is from section 7.17; on-ink, ink-soft and on-ink-soft are derived in the same spirit as Ink's.
    AppAccent.Harbour ->
        if (isDark) InkSet(Color(0xFF8FCDD3), Color(0xFF05282C), Color(0xFF1D4046), Color(0xFFD3EEF1))
        else InkSet(Color(0xFF1F5F66), Color(0xFFFFFFFF), Color(0xFFDCEBEC), Color(0xFF0F3A40))
    AppAccent.Moss ->
        if (isDark) InkSet(Color(0xFFA9CB9C), Color(0xFF14290F), Color(0xFF2F4A29), Color(0xFFE0EFD9))
        else InkSet(Color(0xFF3D6136), Color(0xFFFFFFFF), Color(0xFFE1ECDD), Color(0xFF1F3A1A))
    AppAccent.Plum ->
        if (isDark) InkSet(Color(0xFFD6B4DC), Color(0xFF2C1432), Color(0xFF4A2E50), Color(0xFFF2E1F5))
        else InkSet(Color(0xFF69406F), Color(0xFFFFFFFF), Color(0xFFEBDEEE), Color(0xFF3E2243))
}

/** All tokens for a theme and accent. Pure, so it is testable without Compose. */
fun talleleColors(isDark: Boolean, accent: AppAccent): TalleleColors {
    val ink = inkSet(accent, isDark)
    return if (isDark) {
        TalleleColors(
            isDark = true, accent = accent,
            page = Color(0xFF121417), surface = Color(0xFF1A1D22), sheet = Color(0xFF22262C), well = Color(0xFF0D0F12),
            text = Color(0xFFECEAE4), text2 = Color(0xFFB4B7BE), text3 = Color(0xFF9A9EA7),
            hairline = Color(0xFF2C3037), outline = Color(0xFF6F747D),
            ink = ink.ink, onInk = ink.onInk, inkSoft = ink.inkSoft, onInkSoft = ink.onInkSoft,
            ochre = Color(0xFFE6B566), ochreSoft = Color(0xFF3B2B12), onOchreSoft = Color(0xFFF6DDB2),
            success = Color(0xFF93C6A0),
            // on-error and on-error-soft are derived (not in the proposal table).
            error = Color(0xFFF2A49C), onError = Color(0xFF3B0D0A),
            errorSoft = Color(0xFF4A1F1C), onErrorSoft = Color(0xFFFBD3CE),
            spaces = DarkSpaces,
        )
    } else {
        TalleleColors(
            isDark = false, accent = accent,
            page = Color(0xFFF3F1EC), surface = Color(0xFFFBFAF7), sheet = Color(0xFFFFFFFF), well = Color(0xFFEAE7E0),
            text = Color(0xFF1B1F27), text2 = Color(0xFF4F5562), text3 = Color(0xFF5F646E),
            hairline = Color(0xFFDDD9D0), outline = Color(0xFF8A8780),
            ink = ink.ink, onInk = ink.onInk, inkSoft = ink.inkSoft, onInkSoft = ink.onInkSoft,
            ochre = Color(0xFF8A5300), ochreSoft = Color(0xFFF4E4C9), onOchreSoft = Color(0xFF3F2600),
            success = Color(0xFF2F6B3F),
            error = Color(0xFFA3302A), onError = Color(0xFFFFFFFF),
            errorSoft = Color(0xFFF7DEDA), onErrorSoft = Color(0xFF6E1E1A),
            spaces = LightSpaces,
        )
    }
}

/**
 * Maps the Limestone tokens onto Material roles so existing screens pick up the new look.
 *
 * - background = page; onBackground and onSurface = text; onSurfaceVariant = text-2.
 * - surface = surface. surfaceVariant = well (inputs, search field).
 * - Container roles are chosen by what Material 3 (BOM 2024.12.01) uses them for, so every
 *   overlay resolves to the solid sheet colour: surfaceContainerLowest = surface (plain list
 *   cards); surfaceContainerLow = sheet (ModalBottomSheet default); surfaceContainer = sheet
 *   (DropdownMenu, NavigationBar); surfaceContainerHigh = sheet (AlertDialog, DatePicker);
 *   surfaceContainerHighest = well (filled TextField, search bar).
 *   surfaceDim = page and surfaceBright = surface.
 * - primary, secondary and tertiary are all ink (no second colour); their containers are ink-soft.
 * - Ochre is deliberately NOT mapped to any Material role; it is only reachable through [TalleleColors].
 * - outline = outline; outlineVariant = hairline.
 * - error = error, onError = on-error, errorContainer = error-soft, onErrorContainer = on-error-soft.
 * - Inverse roles (snackbars): inverseSurface = text, inverseOnSurface = page, inversePrimary = the
 *   same accent's ink in the opposite theme, so the action stays readable on the inverse surface.
 * - surfaceTint = surface, so Material tonal elevation never tints a surface: surfaces stay flat and solid.
 * - scrim = black.
 */
fun TalleleColors.toMaterialColorScheme(): ColorScheme {
    val opposite = talleleColors(!isDark, accent)
    return if (isDark) {
        darkColorScheme(
            primary = ink, onPrimary = onInk, primaryContainer = inkSoft, onPrimaryContainer = onInkSoft,
            secondary = ink, onSecondary = onInk, secondaryContainer = inkSoft, onSecondaryContainer = onInkSoft,
            tertiary = ink, onTertiary = onInk, tertiaryContainer = inkSoft, onTertiaryContainer = onInkSoft,
            background = page, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = well, onSurfaceVariant = text2,
            surfaceTint = surface,
            inverseSurface = text, inverseOnSurface = page, inversePrimary = opposite.ink,
            error = error, onError = onError, errorContainer = errorSoft, onErrorContainer = onErrorSoft,
            outline = outline, outlineVariant = hairline,
            scrim = Color.Black,
            surfaceBright = surface, surfaceDim = page,
            surfaceContainerLowest = surface, surfaceContainerLow = sheet, surfaceContainer = sheet,
            surfaceContainerHigh = sheet, surfaceContainerHighest = well,
        )
    } else {
        lightColorScheme(
            primary = ink, onPrimary = onInk, primaryContainer = inkSoft, onPrimaryContainer = onInkSoft,
            secondary = ink, onSecondary = onInk, secondaryContainer = inkSoft, onSecondaryContainer = onInkSoft,
            tertiary = ink, onTertiary = onInk, tertiaryContainer = inkSoft, onTertiaryContainer = onInkSoft,
            background = page, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = well, onSurfaceVariant = text2,
            surfaceTint = surface,
            inverseSurface = text, inverseOnSurface = page, inversePrimary = opposite.ink,
            error = error, onError = onError, errorContainer = errorSoft, onErrorContainer = onErrorSoft,
            outline = outline, outlineVariant = hairline,
            scrim = Color.Black,
            surfaceBright = surface, surfaceDim = page,
            surfaceContainerLowest = surface, surfaceContainerLow = sheet, surfaceContainer = sheet,
            surfaceContainerHigh = sheet, surfaceContainerHighest = well,
        )
    }
}

/** Default (Ink) tokens, used when no theme is provided (previews, tests). */
val LocalTalleleColors = staticCompositionLocalOf { talleleColors(isDark = false, accent = AppAccent.Ink) }
