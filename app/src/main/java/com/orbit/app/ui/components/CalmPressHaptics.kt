package com.orbit.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * Adds one restrained system haptic when an enabled descendant accepts a short press.
 *
 * Pointer changes are only observed, never consumed. Blank areas and disabled controls remain
 * silent because no descendant consumes the completed press. Scrolls, drags, long presses, and
 * multi-touch gestures are also filtered out, while the platform remains responsible for honoring
 * the user's haptic-feedback setting.
 */
@Composable
fun Modifier.calmPressHaptics(): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    return pointerInput(
        hapticFeedback,
        viewConfiguration.touchSlop,
        viewConfiguration.longPressTimeoutMillis,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var maximumDistance = 0f
            var multiplePointers = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.count { it.pressed } > 1) {
                    multiplePointers = true
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                maximumDistance = maxOf(
                    maximumDistance,
                    (change.position - down.position).getDistance(),
                )

                if (!change.pressed) {
                    if (
                        shouldPerformCalmPressHaptic(
                            pressAccepted = change.isConsumed,
                            maximumDistance = maximumDistance,
                            durationMillis = change.uptimeMillis - down.uptimeMillis,
                            touchSlop = viewConfiguration.touchSlop,
                            longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                            multiplePointers = multiplePointers,
                        )
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    break
                }
            }
        }
    }
}

internal fun shouldPerformCalmPressHaptic(
    pressAccepted: Boolean,
    maximumDistance: Float,
    durationMillis: Long,
    touchSlop: Float,
    longPressTimeoutMillis: Long,
    multiplePointers: Boolean,
): Boolean =
    pressAccepted &&
        !multiplePointers &&
        maximumDistance <= touchSlop &&
        durationMillis in 0 until longPressTimeoutMillis

