package com.orbit.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.orbit.app.R
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.reminders.ReminderCapabilities
import com.orbit.app.reminders.ReminderCapabilityStatus
import com.orbit.app.reminders.ReminderHealth
import com.orbit.app.reminders.ReminderHealthFix
import com.orbit.app.reminders.ReminderHealthIssue
import com.orbit.app.reminders.ReminderNotifier
import com.orbit.app.reminders.health
import com.orbit.app.ui.components.TintedIconChip
import kotlinx.coroutines.launch

/**
 * Settings › Reminders health row: one line that is calm when nothing blocks reminders,
 * and exactly one fix button when something does. Re-read on every return to Tallele.
 */
@Composable
internal fun ReminderStatusRow(icon: ImageVector, iconColor: Color) {
    val status = rememberReminderCapabilityStatus()
    val nextNotificationAt = rememberNextReminderNotificationAt()
    val health = status.health(nextNotificationAt = nextNotificationAt, now = System.currentTimeMillis())
    ReminderHealthRow(health = health, icon = icon, iconColor = iconColor)
}

@Composable
internal fun ReminderHealthRow(health: ReminderHealth, icon: ImageVector, iconColor: Color) {
    val context = LocalContext.current
    val samsungSteps = remember(context) { ReminderCapabilities.needsSamsungSteps(context) }
    val showSamsungSteps = health.issue == ReminderHealthIssue.MayBeStopped && samsungSteps
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TintedIconChip(icon = icon, color = iconColor)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(health.issue.messageRes()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                // Red only when reminders can't ring and one is due within a day.
                color = if (health.urgent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (showSamsungSteps) {
                Text(
                    text = stringResource(R.string.reminder_health_samsung_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val fix = health.fix
        val intent = remember(context, health.issue) { ReminderCapabilities.fixIntent(context, health.issue) }
        if (fix != null && intent != null) {
            RowActionButton(
                label = stringResource(fix.labelRes()),
                onClick = { context.startSafely(intent) },
            )
        }
    }
}

/** Re-reads Android's permissions every time the user comes back to Tallele. */
@Composable
internal fun rememberReminderCapabilityStatus(): ReminderCapabilityStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderCapabilities.status(context)) }
    LifecycleResumeEffect(context) {
        status = ReminderCapabilities.status(context)
        onPauseOrDispose { }
    }
    return status
}

/** The next reminder that has not rung yet, re-read on every return to Tallele. */
@Composable
private fun rememberNextReminderNotificationAt(): Long? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var next by remember { mutableStateOf<Long?>(null) }
    LifecycleResumeEffect(context) {
        val job = scope.launch {
            next = runCatching {
                ReminderNotifier.reminderDao(context).nextUndeliveredNotificationAt(System.currentTimeMillis())
            }.getOrNull()
        }
        onPauseOrDispose { job.cancel() }
    }
    return next
}

@StringRes
internal fun ReminderHealthIssue.messageRes(): Int = when (this) {
    ReminderHealthIssue.None -> R.string.reminder_health_ok
    ReminderHealthIssue.NotificationsOff -> R.string.reminder_health_notifications_off
    ReminderHealthIssue.ChannelOff -> R.string.reminder_health_channel_off
    ReminderHealthIssue.MayBeLate -> R.string.reminder_health_late
    ReminderHealthIssue.MayBeStopped -> R.string.reminder_health_battery
}

@StringRes
internal fun ReminderHealthFix.labelRes(): Int = when (this) {
    ReminderHealthFix.TurnOn -> R.string.settings_turn_on
    ReminderHealthFix.Allow -> R.string.settings_allow
    ReminderHealthFix.Check -> R.string.reminder_health_action_check
}

/**
 * Saving: how a new thought is handled. "Sort right after saving" is also the pause for
 * automatic filing (local and Gemini), so it sits in plain sight next to saving, not under
 * reminders (review F11). "Open the keyboard on Home" only changes how Home opens.
 */
@Composable
internal fun SavingSettings(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_sort_right_after_saving),
            body = stringResource(R.string.settings_sort_right_after_saving_body),
            checked = settings.sortRightAfterSaving,
            onCheckedChange = { onSettingsChanged(settings.copy(sortRightAfterSaving = it)) },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_focus_capture),
            body = stringResource(R.string.settings_focus_capture_body),
            checked = settings.focusCaptureOnOpen,
            onCheckedChange = { onSettingsChanged(settings.copy(focusCaptureOnOpen = it)) },
        )
    }
}

/** A whole-row switch: the label and the switch are one target for touch and TalkBack. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The row handles the toggle so the switch is decorative for input.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some devices hide these screens; the status text above still explains the situation.
    } catch (_: SecurityException) {
    }
}
