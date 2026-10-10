package com.orbit.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned off animations ("Remove animations" in accessibility
 * settings, or animator duration scale 0). Screens skip decorative motion then.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val override = LocalReducedMotionOverride.current
    val context = LocalContext.current
    return override ?: remember(context) { isReducedMotion(context) }
}

/** Lets tests and previews force a motion preference. */
val LocalReducedMotionOverride = compositionLocalOf<Boolean?> { null }

fun isReducedMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale == 0f || !ValueAnimator.areAnimatorsEnabled()
}
