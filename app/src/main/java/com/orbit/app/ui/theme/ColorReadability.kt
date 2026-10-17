package com.orbit.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** WCAG 2.x contrast ratio. A translucent foreground is composited over the background first. */
internal fun readabilityRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = foreground.compositeOver(background).luminance()
    val backgroundLuminance = background.luminance()
    return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
}

internal fun meetsTextReadability(foreground: Color, background: Color, minimum: Float): Boolean =
    readabilityRatio(foreground, background) >= minimum

/** WCAG 2.x minimum for normal text. */
internal const val TextContrastMinimum = 4.5f

/** WCAG 2.x minimum for non-text UI (edges, focus rings, icons that carry meaning). */
internal const val UiContrastMinimum = 3f
