package com.orbit.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.ZoneId

/**
 * The save receipt: one quiet line under the capture box about the last thought, with
 * one button. It stays until the next save or until it is swiped away, and its text is
 * swapped in place when the thought's outcome changes; the surface itself does not move.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveReceipt(
    receipt: CaptureReceipt?,
    timeFormat: OrbitTimeFormat,
    reduceMotion: Boolean,
    onAction: (CaptureReceipt, ReceiptAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
) {
    // Kept while the receipt fades out, so it does not go blank first.
    val lastShown = remember { LastShownReceipt() }
    if (receipt != null) lastShown.receipt = receipt
    AnimatedVisibility(
        visible = receipt != null,
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(220)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(220)),
    ) {
        val current = receipt ?: lastShown.receipt ?: return@AnimatedVisibility
        // A new thought gets a new swipe state, so a dismissed receipt never sticks.
        key(current.captureId) {
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) onDismiss()
                    value != SwipeToDismissBoxValue.Settled
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                modifier = Modifier.padding(top = 14.dp),
            ) {
                SaveReceiptCard(
                    receipt = current,
                    timeFormat = timeFormat,
                    isWorking = isWorking,
                    onAction = { action -> onAction(current, action) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private class LastShownReceipt {
    var receipt: CaptureReceipt? = null
}

@Composable
private fun SaveReceiptCard(
    receipt: CaptureReceipt,
    timeFormat: OrbitTimeFormat,
    isWorking: Boolean,
    onAction: (ReceiptAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = receiptText(receipt.line(), timeFormat)
    val action = receipt.action()
    val hideLabel = stringResource(R.string.core_hide)
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                // TalkBack cannot swipe a card away; offer the same as an action.
                customActions = listOf(CustomAccessibilityAction(hideLabel) { onDismiss(); true })
            },
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // One sentence in one node, read politely after current speech when it changes.
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = { onAction(action) }, enabled = !isWorking) {
                Text(stringResource(action.labelRes))
            }
        }
    }
}

/** The receipt's sentence in the current language. */
@Composable
internal fun receiptText(line: ReceiptLine, timeFormat: OrbitTimeFormat): String {
    val todayWord = stringResource(R.string.core_today)
    val tomorrowWord = stringResource(R.string.core_tomorrow)
    val time = line.time?.let { time ->
        val zone = ZoneId.systemDefault()
        formatReceiptTime(time, LocalDate.now(zone), zone, timeFormat, todayWord, tomorrowWord)
    }
    val space = line.space?.let { localizedSpaceName(it) }
    return when {
        time != null -> stringResource(line.textRes, time)
        space != null -> stringResource(line.textRes, space)
        else -> stringResource(line.textRes)
    }
}

/**
 * The single question Home may ask, in place of the receipt: the thought is clearly a
 * reminder, but its time is missing or already past. "Just keep it" keeps the thought
 * saved, in Later and in Search, and nothing rings for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickReminderCard(
    question: QuickReminderQuestion,
    isWorking: Boolean,
    onPickTime: () -> Unit,
    onKeep: () -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (question.reason == CaptureOutcomeReason.TimePassed) {
                        R.string.receipt_time_passed
                    } else {
                        R.string.receipt_needs_time
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = question.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickTime, enabled = !isWorking) {
                    Text(stringResource(R.string.core_home_quick_reminder_pick))
                }
                TextButton(onClick = onKeep, enabled = !isWorking) {
                    Text(stringResource(R.string.receipt_action_keep))
                }
            }
        }
    }
}
