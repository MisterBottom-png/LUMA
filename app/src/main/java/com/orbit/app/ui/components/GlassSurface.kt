package com.orbit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

internal val LocalOrbitHazeState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalOrbitAppearance = staticCompositionLocalOf { AppSettings() }
internal val LocalOrbitUsesCustomBackground = staticCompositionLocalOf { false }

enum class GlassSurfaceStyle {
    Standard,
    Prominent,
    Sheet,
    Subtle,
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassSurfaceStyle = GlassSurfaceStyle.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    val visuals = orbitGlassVisuals(style)
    val hazeState = checkNotNull(LocalOrbitHazeState.current) {
        "GlassSurface must be hosted inside OrbitBackground"
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
                .hazeChild(
                    state = hazeState,
                    style = visuals.hazeStyle,
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
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visuals = orbitSoftSurfaceVisuals()
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = visuals.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, visuals.borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

internal data class GlassVisuals(
    val hazeStyle: HazeStyle,
    val edge: Brush,
    val shadowColor: Color,
    val shadowElevation: androidx.compose.ui.unit.Dp,
)

private data class SoftSurfaceVisuals(
    val containerColor: Color,
    val borderColor: Color,
)

/** Shared visual recipe so every floating glass surface reads as one material. */
@Composable
internal fun orbitGlassVisuals(style: GlassSurfaceStyle = GlassSurfaceStyle.Standard): GlassVisuals {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val glassStrength = LocalOrbitAppearance.current.glassStrength.coerceIn(0f, 1f)
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
    val blurRadius = when (style) {
        GlassSurfaceStyle.Prominent -> if (isDark) 22.dp else 18.dp
        GlassSurfaceStyle.Sheet -> if (isDark) 24.dp else 20.dp
        GlassSurfaceStyle.Subtle -> if (isDark) 14.dp else 12.dp
        GlassSurfaceStyle.Standard -> if (isDark) 20.dp else 16.dp
    }
    val noiseFactor = when (style) {
        GlassSurfaceStyle.Sheet -> if (isDark) 0.040f else 0.030f
        GlassSurfaceStyle.Prominent -> if (isDark) 0.036f else 0.028f
        GlassSurfaceStyle.Subtle -> if (isDark) 0.024f else 0.018f
        GlassSurfaceStyle.Standard -> if (isDark) 0.032f else 0.024f
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
            tint = HazeTint(tint),
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
        },
    )
}

internal fun glassTintAlpha(
    style: GlassSurfaceStyle,
    isDark: Boolean,
    glassStrength: Float,
    hasCustomBackground: Boolean,
): Float {
    val baseTintAlpha = when (style) {
        GlassSurfaceStyle.Prominent -> 0.18f
        GlassSurfaceStyle.Sheet -> 0.24f
        GlassSurfaceStyle.Subtle -> 0.10f
        GlassSurfaceStyle.Standard -> if (isDark) 0.14f else 0.13f
    }
    val tintRange = when (style) {
        GlassSurfaceStyle.Prominent -> if (isDark) 0.28f else 0.24f
        GlassSurfaceStyle.Sheet -> if (isDark) 0.30f else 0.28f
        GlassSurfaceStyle.Subtle -> if (isDark) 0.16f else 0.14f
        GlassSurfaceStyle.Standard -> if (isDark) 0.24f else 0.20f
    }
    val customBackgroundBoost = if (hasCustomBackground) {
        when (style) {
            GlassSurfaceStyle.Prominent -> 0.10f
            GlassSurfaceStyle.Sheet -> 0.06f
            GlassSurfaceStyle.Subtle -> 0.12f
            GlassSurfaceStyle.Standard -> 0.10f
        }
    } else {
        0f
    }
    return (
        baseTintAlpha +
            (glassStrength.coerceIn(0f, 1f) * tintRange) +
            customBackgroundBoost
        ).coerceAtMost(0.82f)
}

@Composable
private fun orbitSoftSurfaceVisuals(): SoftSurfaceVisuals {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val glassStrength = LocalOrbitAppearance.current.glassStrength.coerceIn(0f, 1f)
    val hasCustomBackground = LocalOrbitUsesCustomBackground.current
    return SoftSurfaceVisuals(
        containerColor = if (hasCustomBackground && isDark) {
            Color(0xFF14101D).copy(alpha = 0.42f + (glassStrength * 0.12f))
        } else if (hasCustomBackground) {
            Color.White.copy(alpha = 0.36f + (glassStrength * 0.12f))
        } else if (isDark) {
            colors.surface.copy(alpha = 0.56f + (glassStrength * 0.14f))
        } else {
            Color.White.copy(alpha = 0.46f + (glassStrength * 0.18f))
        },
        borderColor = if (isDark) {
            Color.White.copy(alpha = 0.08f + (glassStrength * 0.08f))
        } else {
            Color.White.copy(alpha = 0.26f + (glassStrength * 0.22f))
        },
    )
}
