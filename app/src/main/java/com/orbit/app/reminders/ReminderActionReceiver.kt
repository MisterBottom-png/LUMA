package com.orbit.app.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.orbit.app.OrbitApplication
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.RoomReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the Done and Snooze buttons on a reminder notification without opening LUMA. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_DONE && action != ACTION_SNOOZE) return
        val reminderId = intent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, 0L)
        if (reminderId == 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                handleReminderAction(repository(context), action, reminderId, now)
                NotificationManagerCompat.from(context)
                    .cancel(reminderNotificationRequestCode(reminderId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun repository(context: Context): ReminderRepository {
        val appContext = context.applicationContext
        return (appContext as? OrbitApplication)?.container?.reminderRepository
            ?: RoomReminderRepository(
                OrbitDatabase.getInstance(appContext).reminderDao(),
                WorkManagerReminderScheduler(appContext),
            )
    }

    companion object {
        const val ACTION_DONE = "com.orbit.app.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.orbit.app.action.REMINDER_SNOOZE"
        const val SnoozeMillis: Long = 15L * 60_000L

        fun pendingIntent(
            context: Context,
            action: String,
            reminderId: Long,
            notificationTime: Long,
        ): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderNotificationWorker.KEY_REMINDER_ID, reminderId)
                putExtra(ReminderNotificationWorker.KEY_NOTIFICATION_TIME, notificationTime)
            }
            val requestCode = 31 * reminderNotificationRequestCode(reminderId) + action.hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/**
 * Applies a notification action. Done completes the reminder (a repeating one moves
 * to its next occurrence); Snooze moves only the notification time, keeping the
 * reminder's own target time and edited timestamp.
 */
internal suspend fun handleReminderAction(
    repository: ReminderRepository,
    action: String,
    reminderId: Long,
    now: Long,
) {
    val reminder = repository.getById(reminderId) ?: return
    if (reminder.completedAt != null) return
    when (action) {
        ReminderActionReceiver.ACTION_DONE -> repository.update(ReminderRepeats.markDone(reminder, now))
        ReminderActionReceiver.ACTION_SNOOZE -> repository.update(
            reminder.copy(snoozedUntil = now + ReminderActionReceiver.SnoozeMillis),
        )
    }
}
