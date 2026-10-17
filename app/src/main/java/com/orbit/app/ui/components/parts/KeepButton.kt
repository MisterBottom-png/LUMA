package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.components.rememberReducedMotion
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * The Keep button: ink filled, on-ink label, the keep mark (proposal 6.1 rule 1 and 6.5).
 *
 * - At least 48 dp tall, and it grows when the label wraps at large text sizes.
 * - [busy] shows a quiet progress in place of the mark and blocks a double tap: [onClick] is not
 *   called while busy. The node stays focusable so TalkBack focus does not jump; [busyDescription]
 *   (owner supplied, for example "Keeping") is exposed as its state.
 * - Haptics: exactly one [HapticFeedbackType.LongPress] tick per accepted click, the shortest system
 *   tick Compose 1.7 offers. A disabled or busy button is silent, and ordinary taps elsewhere are
 *   not touched by this part.
 * - The part never decides whether the keep worked; the owner flips [busy] and clears the editor.
 */
@Composable
fun KeepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyDescription: String? = null,
) {
    val colors = TalleleTheme.colors
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val container = if (enabled) colors.ink else colors.well
    val content = if (enabled) colors.onInk else colors.text3
    Row(
        modifier = modifier
            .heightIn(min = TalleleDimens.MinTouchTarget)
            .widthIn(min = TalleleDimens.MinTouchTarget)
            .clip(TalleleShapes.Card)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button) {
                if (!busy) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            }
            .then(
                if (busy && busyDescription != null) {
                    Modifier.semantics { stateDescription = busyDescription }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            // Reduced motion: a still arc instead of a spinning one.
            if (reducedMotion) {
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.size(20.dp),
                    color = content,
                    strokeWidth = 2.dp,
                    trackColor = container,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = content,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            KeepMark(size = 20.dp, tint = content)
        }
        Text(text = label, style = TalleleTheme.typography.control.copy(color = content))
    }
}
