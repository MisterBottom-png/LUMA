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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.orbit.app.R
import com.orbit.app.ui.reminders.openReminderNotificationSettings
import com.orbit.app.reminders.ReminderCapabilities
import com.orbit.app.reminders.ReminderCapabilityState
import com.orbit.app.reminders.ReminderCapabilityStatus
import com.orbit.app.ui.components.TintedIconChip

/**
 * Whether reminders can reach the user, said plainly, with the one fix right on the row
 * ("Turn on" when notifications are off, "Allow" when exact timing is off).
 */
@Composable
internal fun ReminderStatusRow(icon: ImageVector, iconColor: Color) {
    val context = LocalContext.current
    val status = rememberReminderCapabilityStatus()
    val exactAlarmIntent = if (status.state == ReminderCapabilityState.MayBeLate && status.exactAlarmsAdjustable) {
        ReminderCapabilities.exactAlarmSettingsIntent(context)
    } else {
        null
    }
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
                text = stringResource(R.string.settings_reminder_delivery_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(status.state.messageRes()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (status.state == ReminderCapabilityState.Blocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when {
            status.state == ReminderCapabilityState.Blocked -> RowActionButton(
                label = stringResource(R.string.settings_turn_on),
                onClick = { openReminderNotificationSettings(context) },
            )
            exactAlarmIntent != null -> RowActionButton(
                label = stringResource(R.string.settings_allow),
                onClick = { context.startSafely(exactAlarmIntent) },
            )
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
