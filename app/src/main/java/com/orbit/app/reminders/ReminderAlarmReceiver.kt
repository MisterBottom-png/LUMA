package com.orbit.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER_ALARM) return
        val reminderId = intent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, 0L)
        val notificationTime = intent.getLongExtra(
            ReminderNotificationWorker.KEY_NOTIFICATION_TIME,
            0L,
        )
        if (reminderId == 0L || notificationTime <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderNotifier.showReminderNotification(
                    context = context,
                    reminderId = reminderId,
                    expectedNotificationTime = notificationTime,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_ALARM = "com.orbit.app.action.REMINDER_ALARM"
    }
}
