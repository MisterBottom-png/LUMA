package com.orbit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.TalleleColors
import com.orbit.app.ui.theme.TalleleTheme
import dev.chrisbanes.haze.HazeState

/*
 * Limestone Harbour (proposal v1.3 sections 0, 6.1 and 6.4, decision D-24): the bottom tab bar is the
 * only see-through surface in the app. Everything in this file is solid. The names below
 * (SoftGlassSurface, LiveGlassSurface, GlassSurfaceStyle, ModalSurface) are kept so screens compile
 * unchanged until each moves to the shared parts in its own phase; they now draw opaque token colours
 * with a flat hairline edge, no blur, no noise, no tint and no gradient.
 *
 * The tab bar and its blur live in TalleleTabBar.kt and TabBarLook.kt.
 */

/** The Haze state the background provides. Only the tab bar reads it, to blur what is behind it. */
internal val LocalOrbitHazeState = staticCompositionLocalOf<HazeState?> { null }

/** What the current route allows. The tab bar blurs only on [LiveAllowed] routes. */
internal val LocalGlassRenderingPolicy = staticCompositionLocalOf { GlassRenderingPolicy.LiveAllowed }

/** The person's settings; the tab bar look is derived from them. */
internal val LocalOrbitAppearance = staticCompositionLocalOf { AppSettings() }

enum class GlassRenderingPolicy {
    LiveAllowed,
    SoftOnly,
}

/**
 * Which solid token a surface uses. Kept from the glass era so callers compile; the name no longer
 * implies any transparency.
 */
enum class GlassSurfaceStyle {
    /** A card: [TalleleColors.surface]. */
    Standard,
    Prominent,
    /** Bottom sheets, dialogs: [TalleleColors.sheet]. */
    Sheet,
    Subtle,
    /** The capture box: [TalleleColors.surface]. */
    HomeCapture,
    HomeNavigation,

    /** A small action on a surface: [TalleleColors.inkSoft] with [TalleleColors.onInkSoft]. */
    NavigationAction,
}

/**
 * Formerly live glass. It is solid now: only the tab bar blurs. Kept so existing callers compile.
 */
@Composable
fun LiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    SoftGlassSurface(
        modifier = modifier,
        shape = shape,
        style = style,
        content = content,
    )
}

/**
 * A solid surface: opaque token colour, flat 1 dp hairline edge. A tap on it never reaches what is
 * behind it (Material's [Surface] consumes pointer input and forms a traversal group), and a
 * clickable one keeps press feedback.
 */
@Composable
fun SoftGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    onClick: (() -> Unit)? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TalleleTheme.colors
    val container = solidSurfaceColorFor(style, colors)
    val contentColor = solidSurfaceContentColorFor(style, colors)
    // Only floating layers (sheets, dialogs) lift; cards and the capture box stay flat.
    val elevation = maxOf(shadowElevation, solidSurfaceElevationFor(style))
    val shadowed = modifier.orbitSoftShadow(elevation, shape, orbitShadowColor(colors.isDark))
    val border = BorderStroke(1.dp, colors.hairline)
    if (onClick == null) {
        Surface(
            modifier = shadowed,
            shape = shape,
            color = container,
            contentColor = contentColor,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = { Box(content = content) },
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = shadowed.orbitPressFeedback(
                interactionSource = interactionSource,
                clipShape = shape,
            ),
            shape = shape,
            color = container,
            contentColor = contentColor,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
            content = { Box(content = content) },
        )
    }
}

/** A soft, low shadow for floating layers only. */
internal fun Modifier.orbitSoftShadow(
    elevation: Dp,
    shape: Shape,
    color: Color,
): Modifier = if (elevation <= 0.dp) {
    this
} else {
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = color.copy(alpha = color.alpha * 0.55f),
        spotColor = color,
    )
}

/** Tinted, low-opacity shadow colour for floating layers. */
internal fun orbitShadowColor(isDark: Boolean): Color =
    if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0xFF2A2638).copy(alpha = 0.10f)

/**
 * The opaque fill for a style (section 6.1): cards, the capture box and lists use `surface`; sheets
 * and dialogs use `sheet`; a small action uses `ink-soft`. Always alpha 1.
 */
internal fun solidSurfaceColorFor(style: GlassSurfaceStyle, colors: TalleleColors): Color = when (style) {
    GlassSurfaceStyle.Sheet -> colors.sheet
    GlassSurfaceStyle.NavigationAction -> colors.inkSoft
    GlassSurfaceStyle.Standard,
    GlassSurfaceStyle.Prominent,
    GlassSurfaceStyle.Subtle,
    GlassSurfaceStyle.HomeCapture,
    GlassSurfaceStyle.HomeNavigation,
    -> colors.surface
}

/** Text and icon colour on a style's fill: main text, or on-ink-soft on an ink-soft action. */
internal fun solidSurfaceContentColorFor(style: GlassSurfaceStyle, colors: TalleleColors): Color =
    if (style == GlassSurfaceStyle.NavigationAction) colors.onInkSoft else colors.text

/** Only sheets and dialogs float above the page; everything else is flat. */
internal fun solidSurfaceElevationFor(style: GlassSurfaceStyle): Dp = when (style) {
    GlassSurfaceStyle.Sheet -> OrbitModalDefaults.Elevation
    else -> 0.dp
}

/** A solid sheet. Sheets and dialogs are never see-through. */
@Composable
fun ModalSurface(
    modifier: Modifier = Modifier,
    shape: Shape = OrbitModalDefaults.Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    SoftGlassSurface(
        modifier = modifier,
        shape = shape,
        style = GlassSurfaceStyle.Sheet,
        content = content,
    )
}

object OrbitModalDefaults {
    val Shape: Shape = OrbitShapes.Modal
    val DialogShape: Shape = OrbitShapes.Prominent
    val Elevation = 8.dp
    val HorizontalInset = 24.dp
    val ContentPadding = 24.dp
}

/** The scrim behind a sheet or dialog. */
@Composable
internal fun orbitModalScrimColor(): Color =
    Color.Black.copy(alpha = modalScrimAlpha(TalleleTheme.colors.isDark))

internal fun modalScrimAlpha(isDark: Boolean): Float = if (isDark) 0.44f else 0.32f
