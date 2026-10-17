package com.orbit.app.ui.components

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True when the user turned off animations ("Remove animations" in accessibility
 * settings, or animator duration scale 0). Screens skip decorative motion then.
 *
 * The setting is read again every time the app comes to the front (proposal section 6.5), so
 * changing it in system settings and returning takes effect without restarting the app.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val override = LocalReducedMotionOverride.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduced by remember(context) { mutableStateOf(isReducedMotion(context)) }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduced = isReducedMotion(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return override ?: reduced
}

/** Lets tests and previews force a motion preference. */
val LocalReducedMotionOverride = compositionLocalOf<Boolean?> { null }

fun isReducedMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale == 0f || !ValueAnimator.areAnimatorsEnabled()
}

/**
 * True while Android's battery saver is on. Updates live from the system broadcast, so the tab bar
 * can drop its blur the moment the phone enters battery saver, and bring it back when it leaves.
 */
@Composable
fun rememberBatterySaver(): Boolean {
    val override = LocalBatterySaverOverride.current
    val context = LocalContext.current
    var saving by remember(context) { mutableStateOf(isBatterySaverOn(context)) }
    DisposableEffect(context, override) {
        if (override != null) {
            return@DisposableEffect onDispose {}
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                saving = isBatterySaverOn(context)
            }
        }
        // Protected system broadcast, so the receiver does not need to be exported.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The mode may have changed between the first read and the registration.
        saving = isBatterySaverOn(context)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return override ?: saving
}

/** Lets tests and previews force the battery saver state. */
val LocalBatterySaverOverride = compositionLocalOf<Boolean?> { null }

fun isBatterySaverOn(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
