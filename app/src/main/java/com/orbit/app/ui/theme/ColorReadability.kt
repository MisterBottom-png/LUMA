package com.orbit.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

internal fun readabilityRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = foreground.compositeOver(background).luminance()
    val backgroundLuminance = background.luminance()
    return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
}

internal fun meetsTextReadability(foreground: Color, background: Color, minimum: Float): Boolean =
    readabilityRatio(foreground, background) >= minimum
