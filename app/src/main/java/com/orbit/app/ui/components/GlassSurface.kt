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
    NavigationAction,
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
                .shadow(
                    elevation = visuals.shadowElevation,
                    shape = shape,
                    ambientColor = visuals.shadowColor.copy(alpha = 0.10f),
                    spotColor = visuals.shadowColor.copy(alpha = 0.14f),
                )
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
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.borderColor),
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            content = { Box(content = content) },
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.orbitPressFeedback(interactionSource),
            shape = shape,
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.borderColor),
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            interactionSource = interactionSource,
            content = { Box(content = content) },
        )
    }
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
    val borderColor: Color,
)

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
        GlassSurfaceStyle.NavigationAction -> if (isDark) 18.dp else 14.dp
    }
    val noiseFactor = when (style) {
        GlassSurfaceStyle.Sheet -> if (isDark) 0.040f else 0.030f
        GlassSurfaceStyle.Prominent -> if (isDark) 0.036f else 0.028f
        GlassSurfaceStyle.Subtle -> if (isDark) 0.024f else 0.018f
        GlassSurfaceStyle.Standard -> if (isDark) 0.032f else 0.024f
        GlassSurfaceStyle.HomeCapture -> if (isDark) 0.036f else 0.028f
        GlassSurfaceStyle.NavigationAction -> if (isDark) 0.026f else 0.020f
    }

    val edgeStrength = 0.55f + (glassStrength * 0.45f)

    val edge = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.30f * edgeStrength),
                Color.White.copy(alpha = 0.10f * edgeStrength),
                Color.Transparent,
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.58f * edgeStrength),
                Color.White.copy(alpha = 0.18f * edgeStrength),
                Color.Transparent,
                Color.Transparent,
            )
        },
        start = Offset.Zero,
        end = Offset.Infinite,
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
        shadowColor = if (isDark) Color(0xFF08050F) else Color(0xFF706586),
        shadowElevation = when (style) {
            GlassSurfaceStyle.Sheet -> 10.dp
            GlassSurfaceStyle.Prominent -> 8.dp
            GlassSurfaceStyle.Subtle -> 3.dp
            GlassSurfaceStyle.Standard -> 5.dp
            GlassSurfaceStyle.HomeCapture -> 5.dp
            GlassSurfaceStyle.NavigationAction -> 2.dp
        },
        fallbackColor = tint,
    )
}

internal fun glassAccentTintAlpha(style: GlassSurfaceStyle, isDark: Boolean): Float = when (style) {
    GlassSurfaceStyle.Subtle -> if (isDark) 0.045f else 0.035f
    GlassSurfaceStyle.Standard -> if (isDark) 0.050f else 0.040f
    GlassSurfaceStyle.Prominent -> if (isDark) 0.055f else 0.045f
    GlassSurfaceStyle.Sheet -> if (isDark) 0.045f else 0.035f
    GlassSurfaceStyle.HomeCapture -> if (isDark) 0.050f else 0.040f
    GlassSurfaceStyle.NavigationAction -> if (isDark) 0.040f else 0.030f
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
        GlassSurfaceStyle.NavigationAction -> 0.01f
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
    )
    return SoftSurfaceVisuals(
        containerColor = when (style) {
            GlassSurfaceStyle.Subtle -> colors.surfaceContainerLow
            GlassSurfaceStyle.Standard -> colors.surfaceContainer
            GlassSurfaceStyle.Prominent -> colors.surfaceContainerHigh
            GlassSurfaceStyle.Sheet -> colors.surfaceContainerHigh
            GlassSurfaceStyle.HomeCapture -> colors.surfaceContainerHigh
            GlassSurfaceStyle.NavigationAction -> colors.primaryContainer
        }.withAlpha(containerAlpha),
        borderColor = when (style) {
            GlassSurfaceStyle.Subtle -> colors.outlineVariant.copy(alpha = if (isDark) 0.22f else 0.18f)
            GlassSurfaceStyle.HomeCapture -> colors.outlineVariant.copy(alpha = 0.56f)
            GlassSurfaceStyle.NavigationAction -> colors.primary.copy(alpha = if (isDark) 0.16f else 0.10f)
            else -> colors.outline
        },
    )
}

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

    if (style == GlassSurfaceStyle.Sheet) {
        val themeFloor = if (isDark) 0.74f else 0.70f
        val customBackgroundBoost = if (hasCustomBackground) 0.02f else 0f
        return (
            themeFloor +
                (glassStrength.coerceIn(0f, 1f) * 0.18f) +
                customBackgroundBoost
            ).coerceIn(0.70f, 0.94f)
    }

    val roleAdjustment = when (style) {
        GlassSurfaceStyle.Subtle -> -0.03f
        GlassSurfaceStyle.Standard -> 0f
        GlassSurfaceStyle.Prominent -> 0.04f
        GlassSurfaceStyle.Sheet -> 0f
        GlassSurfaceStyle.HomeCapture -> 0.02f
        GlassSurfaceStyle.NavigationAction -> 0.01f
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
