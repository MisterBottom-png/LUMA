package com.orbit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Small spacing scale for repeated layout rhythm; component-specific dimensions stay local. */
object OrbitSpacing {
    val ExtraSmall = 2.dp
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Comfortable = 20.dp
    val ExtraLarge = 24.dp
    val Huge = 32.dp
    val Giant = 48.dp
    val Display = 64.dp
}

/**
 * Shared shapes for comparable surfaces.
 *
 * Pills and circles remain explicit geometry exceptions and should continue to use
 * [androidx.compose.foundation.shape.CircleShape] or a component-specific pill shape.
 */
object OrbitShapes {
    val Small = RoundedCornerShape(14.dp)
    val Standard = RoundedCornerShape(18.dp)
    val Prominent = RoundedCornerShape(28.dp)
    val Modal = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}

/**
 * Calm motion roles shared by navigation and interactive surfaces.
 *
 * Compose animation specs respect Android's system animator duration scale, including
 * the Remove animations accessibility setting. Keep motion finite and purposeful.
 */
object OrbitMotion {
    const val QuickDurationMillis = 120
    const val StandardDurationMillis = 220
    const val EmphasizedDurationMillis = 320

    const val PressedScale = 0.975f
}

object OrbitStateLayer {
    const val Hover = 0.08f
    const val Focus = 0.10f
    const val Pressed = 0.12f
    const val Dragged = 0.16f
    const val Selected = 0.14f
}
