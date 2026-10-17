package com.orbit.app.ui.components.parts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The keep mark geometry in a unit square (0..1 on both axes, y grows downward), so it can be unit
 * tested without drawing. A dot settles into a shallow tray: the dot is centred above the tray, and
 * the tray is a shallow quadratic arc whose ends sit higher than its middle.
 */
internal data class KeepMarkGeometry(
    val dotCenter: Offset,
    val dotRadius: Float,
    val trayStart: Offset,
    /** The quadratic control point; the arc's lowest point is halfway between it and the ends. */
    val trayControl: Offset,
    val trayEnd: Offset,
    val strokeWidth: Float,
) {
    /** Horizontal middle of the tray. */
    val trayCenterX: Float get() = (trayStart.x + trayEnd.x) / 2f

    /** Y of the lowest point of the arc (a quadratic's midpoint is a quarter/half/quarter blend). */
    val trayBottomY: Float get() = 0.25f * trayStart.y + 0.5f * trayControl.y + 0.25f * trayEnd.y

    /** How far the middle of the tray sits below its ends, relative to the tray's width. */
    val trayDepthRatio: Float get() = (trayBottomY - trayStart.y) / (trayEnd.x - trayStart.x)

    /** Bottom edge of the dot. */
    val dotBottomY: Float get() = dotCenter.y + dotRadius
}

internal fun keepMarkGeometry(): KeepMarkGeometry = KeepMarkGeometry(
    dotCenter = Offset(0.5f, 0.30f),
    dotRadius = 0.15f,
    trayStart = Offset(0.14f, 0.58f),
    trayControl = Offset(0.5f, 0.92f),
    trayEnd = Offset(0.86f, 0.58f),
    strokeWidth = 0.10f,
)

/**
 * The keep mark (proposal 6.6): "put away for safekeeping". Drawn with a Canvas, so it needs no
 * resource. Decorative unless [contentDescription] is given (the owner says what it means).
 */
@Composable
fun KeepMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val geometry = keepMarkGeometry()
    val semanticsModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }
    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        val unit = this.size.minDimension
        drawCircle(
            color = color,
            radius = geometry.dotRadius * unit,
            center = geometry.dotCenter * unit,
        )
        val tray = Path().apply {
            moveTo(geometry.trayStart.x * unit, geometry.trayStart.y * unit)
            quadraticTo(
                geometry.trayControl.x * unit, geometry.trayControl.y * unit,
                geometry.trayEnd.x * unit, geometry.trayEnd.y * unit,
            )
        }
        drawPath(
            path = tray,
            color = color,
            style = Stroke(width = geometry.strokeWidth * unit, cap = StrokeCap.Round),
        )
    }
}
