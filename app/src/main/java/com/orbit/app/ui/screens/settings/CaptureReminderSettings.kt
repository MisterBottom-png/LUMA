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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.orbit.app.R
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.reminders.ReminderCapabilities
import com.orbit.app.reminders.ReminderCapabilityState
import com.orbit.app.reminders.ReminderCapabilityStatus

/** Capture behaviour and an honest view of whether reminders can reach the user. */
@Composable
internal fun CaptureAndRemindersSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppearanceCard {
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
        ReminderDeliveryCard()
    }
}

@Composable
private fun ReminderDeliveryCard() {
    val context = LocalContext.current
    val status = rememberReminderCapabilityStatus()
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_reminder_delivery_title)) {
            Text(
                text = stringResource(status.state.messageRes()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.state == ReminderCapabilityState.Blocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (status.state == ReminderCapabilityState.Blocked) {
                OutlinedButton(
                    onClick = { context.startSafely(ReminderCapabilities.notificationSettingsIntent(context)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_open_notification_settings))
                }
            }
            if (status.state == ReminderCapabilityState.MayBeLate && status.exactAlarmsAdjustable) {
                ReminderCapabilities.exactAlarmSettingsIntent(context)?.let { intent ->
                    OutlinedButton(
                        onClick = { context.startSafely(intent) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.settings_allow_exact_alarms))
                    }
                }
            }
        }
    }
}

/** Re-reads Android's permissions every time the user comes back to LUMA. */
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

@StringRes
internal fun ReminderCapabilityState.messageRes(): Int = when (this) {
    ReminderCapabilityState.Ready -> R.string.settings_reminder_delivery_ready
    ReminderCapabilityState.MayBeLate -> R.string.settings_reminder_delivery_may_be_late
    ReminderCapabilityState.Blocked -> R.string.settings_reminder_delivery_blocked
}

@StringRes
internal fun ReminderCapabilityState.statusRes(): Int = when (this) {
    ReminderCapabilityState.Ready -> R.string.settings_status_reminders_ready
    ReminderCapabilityState.MayBeLate -> R.string.settings_status_reminders_may_be_late
    ReminderCapabilityState.Blocked -> R.string.settings_status_reminders_blocked
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
