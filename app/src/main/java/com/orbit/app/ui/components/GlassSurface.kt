package com.orbit.app.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.GlassEffect
import com.orbit.app.ui.theme.OrbitShapes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val LocalOrbitHazeState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalGlassRenderingPolicy = staticCompositionLocalOf { GlassRenderingPolicy.LiveAllowed }
internal val LocalOrbitAppearance = staticCompositionLocalOf { AppSettings() }
internal val LocalOrbitUsesCustomBackground = staticCompositionLocalOf { false }

enum class GlassRenderingPolicy {
    LiveAllowed,
    SoftOnly,
}

enum class GlassSurfaceStyle {
    Standard,
    Prominent,
    Sheet,
    Subtle,
    HomeCapture,
    HomeNavigation,
    NavigationAction,

    /** The bottom bar: near-solid so scrolling content never mixes with its labels. */
    NavigationBar,
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun LiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    val visuals = orbitGlassVisuals(style)
    val hazeState = LocalOrbitHazeState.current
    val liveGlassEnabled = style == GlassSurfaceStyle.Prominent && shouldRenderLiveGlass(
        policy = LocalGlassRenderingPolicy.current,
        platformApi = Build.VERSION.SDK_INT,
        hasHazeSource = hazeState != null,
    )

    if (!liveGlassEnabled) {
        SoftGlassSurface(
            modifier = modifier,
            shape = shape,
            style = style,
            shadowElevation = visuals.shadowElevation,
            content = content,
        )
        return
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier = modifier
                .orbitSoftShadow(visuals.shadowElevation, shape, visuals.shadowColor)
                .clip(shape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = visuals.hazeStyle,
                        ) {
                            inputScale = HazeInputScale.Auto
                            progressive = null
                        }
                    } else {
                        Modifier.background(visuals.fallbackColor)
                    },
                )
                .border(1.dp, visuals.edge, shape),
            content = content,
        )
    }
}

@Composable
fun SoftGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    onClick: (() -> Unit)? = null,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val visuals = orbitSoftSurfaceVisuals(style)
    // Only the floating layer (bar, capture box, sheets) casts a shadow; content cards
    // stay flat so no grey shadow shows through their translucent fill.
    val elevation = maxOf(shadowElevation, visuals.floatingElevation)
    val shadowed = modifier.orbitSoftShadow(elevation, shape, visuals.shadowColor)
    if (onClick == null) {
        Surface(
            modifier = shadowed,
            shape = shape,
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.edge),
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
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.edge),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
            content = { Box(content = content) },
        )
    }
}

/**
 * A soft, low, tinted shadow. Large blur with low opacity reads as light; a small dark
 * shadow reads as a box drawn on paper.
 */
internal fun Modifier.orbitSoftShadow(
    elevation: androidx.compose.ui.unit.Dp,
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

/**
 * One edge for every surface: a hairline lit from above. Light theme fades from a white
 * highlight to a faint ink line at the bottom; dark theme from a soft white to almost nothing.
 * It replaces solid grey outlines, which make glass read as a form field.
 */
@Composable
internal fun orbitGlassEdgeBrush(strength: Float = 1f): Brush {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    return glassEdgeBrush(isDark = isDark, ink = colors.onSurface, strength = strength)
}

internal fun glassEdgeBrush(isDark: Boolean, ink: Color, strength: Float = 1f): Brush {
    val k = strength.coerceIn(0f, 1f)
    return Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.16f * k),
                Color.White.copy(alpha = 0.06f * k),
                Color.White.copy(alpha = 0.03f * k),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.95f * k),
                Color.White.copy(alpha = 0.55f * k),
                ink.copy(alpha = 0.07f * k),
            )
        },
    )
}

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
        shadowElevation = OrbitModalDefaults.Elevation,
        content = content,
    )
}

object OrbitModalDefaults {
    val Shape: Shape = OrbitShapes.Modal
    val DialogShape: Shape = OrbitShapes.Prominent
    val Elevation = 10.dp
    val HorizontalInset = 24.dp
    val ContentPadding = 24.dp
}

@Composable
internal fun orbitModalScrimColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Color.Black.copy(alpha = modalScrimAlpha(isDark))
}

internal fun modalScrimAlpha(isDark: Boolean): Float = if (isDark) 0.44f else 0.32f

internal fun shouldRenderLiveGlass(
    policy: GlassRenderingPolicy,
    platformApi: Int,
    hasHazeSource: Boolean,
): Boolean = policy == GlassRenderingPolicy.LiveAllowed &&
    platformApi >= Build.VERSION_CODES.S &&
    hasHazeSource

internal data class GlassVisuals(
    val hazeStyle: HazeStyle,
    val edge: Brush,
    val shadowColor: Color,
    val shadowElevation: androidx.compose.ui.unit.Dp,
    val fallbackColor: Color,
)

internal data class SoftSurfaceVisuals(
    val containerColor: Color,
    val edge: Brush,
    val shadowColor: Color,
    val floatingElevation: androidx.compose.ui.unit.Dp,
)

/** Tinted, low-opacity shadow colour shared by every floating surface. */
internal fun orbitShadowColor(isDark: Boolean): Color =
    if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0xFF2A2638).copy(alpha = 0.10f)

/** Elevation is reserved for the floating layer; content cards sit flat on the background. */
internal fun floatingElevationFor(style: GlassSurfaceStyle): androidx.compose.ui.unit.Dp = when (style) {
    GlassSurfaceStyle.NavigationBar -> 14.dp
    GlassSurfaceStyle.Sheet -> 18.dp
    GlassSurfaceStyle.HomeCapture -> 10.dp
    GlassSurfaceStyle.NavigationAction -> 6.dp
    GlassSurfaceStyle.Prominent,
    GlassSurfaceStyle.Standard,
    GlassSurfaceStyle.Subtle,
    GlassSurfaceStyle.HomeNavigation,
    -> 0.dp
}

/** Shared visual recipe so every floating glass surface reads as one material. */
@Composable
internal fun orbitGlassVisuals(style: GlassSurfaceStyle = GlassSurfaceStyle.Standard): GlassVisuals {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val glassStrength = LocalOrbitAppearance.current.glassPreference.legacyStrength
    val hasCustomBackground = LocalOrbitUsesCustomBackground.current
    val tintAlpha = glassTintAlpha(
        style = style,
        isDark = isDark,
        glassStrength = glassStrength,
        hasCustomBackground = hasCustomBackground,
    )
    val tint = if (isDark) {
        Color(0xFF14101D).copy(alpha = tintAlpha)
    } else {
        Color.White.copy(alpha = tintAlpha)
    }
    val accentTintAlpha = glassAccentTintAlpha(style = style, isDark = isDark)
    val accentTint = Brush.linearGradient(
        colors = listOf(
            colors.primary.copy(alpha = accentTintAlpha),
            colors.primary.copy(alpha = accentTintAlpha * 0.64f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    val blurRadius = when (style) {
        GlassSurfaceStyle.Prominent -> if (isDark) 22.dp else 18.dp
        GlassSurfaceStyle.Sheet -> if (isDark) 24.dp else 20.dp
        GlassSurfaceStyle.Subtle -> if (isDark) 14.dp else 12.dp
        GlassSurfaceStyle.Standard -> if (isDark) 20.dp else 16.dp
        GlassSurfaceStyle.HomeCapture -> if (isDark) 22.dp else 18.dp
        GlassSurfaceStyle.HomeNavigation -> if (isDark) 14.dp else 12.dp
        GlassSurfaceStyle.NavigationAction -> if (isDark) 18.dp else 14.dp
        GlassSurfaceStyle.NavigationBar -> if (isDark) 12.dp else 10.dp
    }
    val noiseFactor = when (style) {
        GlassSurfaceStyle.Sheet -> if (isDark) 0.040f else 0.030f
        GlassSurfaceStyle.Prominent -> if (isDark) 0.036f else 0.028f
        GlassSurfaceStyle.Subtle -> if (isDark) 0.024f else 0.018f
        GlassSurfaceStyle.Standard -> if (isDark) 0.032f else 0.024f
        GlassSurfaceStyle.HomeCapture -> if (isDark) 0.036f else 0.028f
        GlassSurfaceStyle.HomeNavigation -> if (isDark) 0.024f else 0.018f
        GlassSurfaceStyle.NavigationAction -> if (isDark) 0.026f else 0.020f
        GlassSurfaceStyle.NavigationBar -> if (isDark) 0.020f else 0.016f
    }

    val edge = glassEdgeBrush(
        isDark = isDark,
        ink = colors.onSurface,
        strength = 0.70f + (glassStrength * 0.30f),
    )

    return GlassVisuals(
        hazeStyle = HazeStyle(
            backgroundColor = colors.background,
            tints = listOf(
                HazeTint(tint),
                HazeTint(accentTint),
            ),
            blurRadius = blurRadius,
            noiseFactor = noiseFactor,
            fallbackTint = HazeTint(tint),
        ),
        edge = edge,
        shadowColor = orbitShadowColor(isDark),
        shadowElevation = floatingElevationFor(style),
        fallbackColor = tint,
    )
}

internal fun glassAccentTintAlpha(style: GlassSurfaceStyle, isDark: Boolean): Float = when (style) {
    GlassSurfaceStyle.Subtle -> if (isDark) 0.045f else 0.035f
    GlassSurfaceStyle.Standard -> if (isDark) 0.050f else 0.040f
    GlassSurfaceStyle.Prominent -> if (isDark) 0.055f else 0.045f
    GlassSurfaceStyle.Sheet -> if (isDark) 0.045f else 0.035f
    GlassSurfaceStyle.HomeCapture -> if (isDark) 0.050f else 0.040f
    GlassSurfaceStyle.HomeNavigation -> if (isDark) 0.045f else 0.035f
    GlassSurfaceStyle.NavigationAction -> if (isDark) 0.040f else 0.030f
    GlassSurfaceStyle.NavigationBar -> if (isDark) 0.045f else 0.035f
}

internal fun glassTintAlpha(
    style: GlassSurfaceStyle,
    isDark: Boolean,
    glassStrength: Float,
    hasCustomBackground: Boolean,
): Float {
    val roleAdjustment = when (style) {
        GlassSurfaceStyle.Subtle -> -0.02f
        GlassSurfaceStyle.Standard -> 0f
        GlassSurfaceStyle.Prominent -> 0.04f
        GlassSurfaceStyle.Sheet -> 0.08f
        GlassSurfaceStyle.HomeCapture -> 0.02f
        GlassSurfaceStyle.HomeNavigation -> -0.01f
        GlassSurfaceStyle.NavigationAction -> 0.01f
        GlassSurfaceStyle.NavigationBar -> 0.20f
    }
    val themeAdjustment = if (isDark) 0.02f else 0f
    val customBackgroundBoost = if (hasCustomBackground) 0.05f else 0f
    return (
        0.04f +
            (glassStrength.coerceIn(0f, 1f) * 0.62f) +
            roleAdjustment +
            themeAdjustment +
            customBackgroundBoost
        ).coerceIn(0.04f, 0.82f)
}

@Composable
internal fun orbitSoftSurfaceVisuals(
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
): SoftSurfaceVisuals {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val glassStrength = LocalOrbitAppearance.current.glassPreference.legacyStrength
    val hasCustomBackground = LocalOrbitUsesCustomBackground.current
    val containerAlpha = softGlassContainerAlpha(
        style = style,
        isDark = isDark,
        glassStrength = glassStrength,
        hasCustomBackground = hasCustomBackground,
    ).let { alpha ->
        if (LocalOrbitAppearance.current.glassEffect == GlassEffect.Off) solidSurfaceAlpha(alpha) else alpha
    }
    // Light theme: surfaces are lighter than the page (frosted white), never greyer.
    // Dark theme: surfaces step up in tone instead of using shadows.
    val container = if (isDark) {
        when (style) {
            GlassSurfaceStyle.Subtle -> colors.surfaceContainerLow
            GlassSurfaceStyle.Standard -> colors.surfaceContainer
            GlassSurfaceStyle.NavigationAction -> colors.primaryContainer
            else -> colors.surfaceContainerHigh
        }
    } else {
        when (style) {
            GlassSurfaceStyle.NavigationAction -> colors.primaryContainer
            else -> colors.surfaceContainerLowest
        }
    }
    return SoftSurfaceVisuals(
        containerColor = container.withAlpha(containerAlpha),
        edge = glassEdgeBrush(
            isDark = isDark,
            ink = colors.onSurface,
            strength = when (style) {
                GlassSurfaceStyle.Subtle, GlassSurfaceStyle.HomeNavigation -> 0.7f
                GlassSurfaceStyle.NavigationAction -> 0.5f
                else -> 1f
            },
        ),
        shadowColor = orbitShadowColor(isDark),
        floatingElevation = floatingElevationFor(style),
    )
}

/** Glass effect "Off": surfaces become solid enough that nothing shows through. */
internal fun solidSurfaceAlpha(alpha: Float): Float = maxOf(alpha, SolidSurfaceAlpha)

internal const val SolidSurfaceAlpha = 0.97f

/** Strong is the only level that may draw live blur; Soft and Off never do. */
internal fun glassRenderingPolicyFor(effect: GlassEffect, routePolicy: GlassRenderingPolicy): GlassRenderingPolicy =
    if (effect == GlassEffect.Strong) routePolicy else GlassRenderingPolicy.SoftOnly

internal fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

internal fun softGlassContainerAlpha(
    style: GlassSurfaceStyle,
    isDark: Boolean,
    glassStrength: Float,
    hasCustomBackground: Boolean,
): Float {
    if (style == GlassSurfaceStyle.NavigationAction) {
        return if (isDark) 0.84f else 0.92f
    }

    if (style == GlassSurfaceStyle.NavigationBar) {
        val customBackgroundBoost = if (hasCustomBackground) 0.03f else 0f
        // Text scrolling under a soft (unblurred) bar must not show through as a second
        // layer of text, so the bar stays close to solid.
        return ((if (isDark) 0.93f else 0.95f) + customBackgroundBoost).coerceAtMost(0.96f)
    }

    if (style == GlassSurfaceStyle.Sheet) {
        // Sheets and dialogs are drawn without blur, so anything lighter lets the list
        // behind read through the sheet's own text. They stay solid.
        return SolidSurfaceAlpha
    }

    if (style == GlassSurfaceStyle.HomeNavigation) {
        val themeFloor = if (isDark) 0.26f else 0.20f
        val customBackgroundBoost = if (hasCustomBackground) 0.06f else 0f
        return (
            themeFloor +
                (glassStrength.coerceIn(0f, 1f) * 0.45f) +
                customBackgroundBoost
            ).coerceIn(0.20f, 0.77f)
    }

    if (style == GlassSurfaceStyle.HomeCapture) {
        val themeFloor = if (isDark) 0.36f else 0.30f
        val customBackgroundBoost = if (hasCustomBackground) 0.10f else 0f
        val maximumAlpha = if (isDark) 0.91f else 0.85f
        return (
            themeFloor +
                (glassStrength.coerceIn(0f, 1f) * 0.45f) +
                customBackgroundBoost
            ).coerceIn(0.30f, maximumAlpha)
    }

    val roleAdjustment = when (style) {
        GlassSurfaceStyle.Subtle -> -0.03f
        GlassSurfaceStyle.Standard -> 0f
        GlassSurfaceStyle.Prominent -> 0.04f
        GlassSurfaceStyle.Sheet -> 0f
        GlassSurfaceStyle.HomeCapture -> 0.02f
        GlassSurfaceStyle.HomeNavigation -> -0.01f
        GlassSurfaceStyle.NavigationAction -> 0.01f
        GlassSurfaceStyle.NavigationBar -> 0.20f
    }
    val themeAdjustment = if (isDark) 0.03f else 0f
    val customBackgroundBoost = if (hasCustomBackground) 0.04f else 0f
    return (
        0.08f +
            (glassStrength.coerceIn(0f, 1f) * 0.62f) +
            roleAdjustment +
            themeAdjustment +
            customBackgroundBoost
        ).coerceIn(0.12f, 0.85f)
}
