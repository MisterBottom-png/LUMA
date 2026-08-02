package com.orbit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.OrbitMotion

/** Adds restrained tactile feedback without changing click handling or semantics. */
@Composable
fun Modifier.orbitPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) OrbitMotion.PressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                OrbitMotion.QuickDurationMillis
            } else {
                OrbitMotion.StandardDurationMillis
            },
        ),
        label = "LUMA press feedback",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Lets scrolling content dissolve into the existing screen background at fixed edges. */
fun Modifier.orbitScrollEdgeFade(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier {
    if (top <= 0.dp && bottom <= 0.dp) return this

    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()

        val topPx = top.toPx().coerceAtMost(size.height)
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color.Black,
                    ),
                    startY = 0f,
                    endY = topPx,
                ),
                size = Size(size.width, topPx),
                blendMode = BlendMode.DstIn,
            )
        }

        val bottomPx = bottom.toPx().coerceAtMost(size.height)
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomPx,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}
