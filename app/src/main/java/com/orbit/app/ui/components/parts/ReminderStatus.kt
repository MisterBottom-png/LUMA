package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * What is really true about a reminder, as the owner knows it. The part never infers it
 * (CLAUDE.md: never say "scheduled" without proof from the real state).
 */
sealed interface ReminderDelivery {
    /** The reminder is really scheduled. The only state drawn in the success colour. */
    data class Scheduled(val text: String) : ReminderDelivery

    /** Not scheduled, for example because notifications are off or no time is set. */
    data class NotScheduled(
        val text: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : ReminderDelivery

    /** Scheduling failed or the system may delay it. Drawn with the error colour. */
    data class Problem(
        val text: String,
        val actionLabel: String,
        val onAction: () -> Unit,
    ) : ReminderDelivery
}

/**
 * Reminder status plus three separate rows: Due, Notify and Repeat (proposal 7.11). Due time,
 * notification time and repeat are never merged into one line. Each row takes its label and value
 * from the owner and may open its own editor through its click callback.
 *
 * The status line always has an icon and words, and is a polite live region. Ochre is used only for
 * [ReminderDelivery.NotScheduled] when the owner offers an action (a choice is needed).
 */
@Composable
fun ReminderStatus(
    delivery: ReminderDelivery,
    dueLabel: String,
    dueValue: String,
    notifyLabel: String,
    notifyValue: String,
    repeatLabel: String,
    repeatValue: String,
    modifier: Modifier = Modifier,
    onDueClick: (() -> Unit)? = null,
    onNotifyClick: (() -> Unit)? = null,
    onRepeatClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeliveryLine(delivery)
        Column {
            FactRow(label = dueLabel, value = dueValue, onClick = onDueClick)
            FactRow(label = notifyLabel, value = notifyValue, onClick = onNotifyClick)
            FactRow(label = repeatLabel, value = repeatValue, onClick = onRepeatClick, showDivider = false)
        }
    }
}

@Composable
private fun DeliveryLine(delivery: ReminderDelivery) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    val text: String
    val actionLabel: String?
    val onAction: (() -> Unit)?
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val iconTint: androidx.compose.ui.graphics.Color
    val container: androidx.compose.ui.graphics.Color
    val textColor: androidx.compose.ui.graphics.Color
    when (delivery) {
        is ReminderDelivery.Scheduled -> {
            text = delivery.text; actionLabel = null; onAction = null
            icon = Icons.Rounded.CheckCircle; iconTint = colors.success
            container = colors.surface; textColor = colors.text
        }

        is ReminderDelivery.NotScheduled -> {
            text = delivery.text; actionLabel = delivery.actionLabel; onAction = delivery.onAction
            icon = Icons.Rounded.NotificationsOff
            val needsChoice = actionLabel != null && onAction != null
            iconTint = if (needsChoice) colors.ochre else colors.text2
            container = if (needsChoice) colors.ochreSoft else colors.surface
            textColor = if (needsChoice) colors.onOchreSoft else colors.text
        }

        is ReminderDelivery.Problem -> {
            text = delivery.text; actionLabel = delivery.actionLabel; onAction = delivery.onAction
            icon = Icons.Rounded.ErrorOutline; iconTint = colors.error
            container = colors.errorSoft; textColor = colors.onErrorSoft
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TalleleShapes.Card)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = if (actionLabel != null) 0.dp else 8.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Text(text = text, style = typography.body.copy(color = textColor), modifier = Modifier.weight(1f))
        }
        if (actionLabel != null && onAction != null) {
            Row(Modifier.fillMaxWidth()) {
                PartTextAction(
                    label = actionLabel,
                    onClick = onAction,
                    color = if (delivery is ReminderDelivery.Problem) colors.onErrorSoft else colors.ink,
                )
            }
        }
    }
}
