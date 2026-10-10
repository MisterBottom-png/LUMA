package com.orbit.app.ui.reminders

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.orbit.app.R
import com.orbit.app.reminders.ReminderCapabilities
import com.orbit.app.reminders.ReminderSaveOutcome

/** The one place that turns a [ReminderSaveOutcome] into words. */
@StringRes
internal fun ReminderSaveOutcome.messageRes(): Int = when (this) {
    ReminderSaveOutcome.Saved -> R.string.reminder_outcome_saved
    ReminderSaveOutcome.SavedNotScheduled -> R.string.reminder_outcome_not_scheduled
    ReminderSaveOutcome.SavedNotificationsBlocked -> R.string.reminder_outcome_notifications_off
}

/** Only "notifications are off" has something the user can fix: "Turn on". */
internal val ReminderSaveOutcome.offersTurnOn: Boolean
    get() = this == ReminderSaveOutcome.SavedNotificationsBlocked

/**
 * "Turn on": opens Tallele's notification settings, the same place Settings ›
 * Reminder delivery opens.
 */
internal fun openReminderNotificationSettings(context: Context) {
    runCatching { context.startActivity(ReminderCapabilities.notificationSettingsIntent(context)) }
}

@Composable
internal fun rememberTurnOnReminderNotifications(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { openReminderNotificationSettings(context) } }
}

/**
 * After an Undo snackbar for a saved reminder: when notifications are off, offer
 * "Turn on" in a second, separate snackbar (one snackbar holds only one action).
 */
internal suspend fun SnackbarHostState.offerTurnOnIfBlocked(
    outcome: ReminderSaveOutcome?,
    prompt: String,
    turnOnLabel: String,
    onTurnOn: () -> Unit,
) {
    if (outcome?.offersTurnOn != true) return
    val result = showSnackbar(message = prompt, actionLabel = turnOnLabel, duration = SnackbarDuration.Long)
    if (result == SnackbarResult.ActionPerformed) onTurnOn()
}
