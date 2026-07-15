package com.orbit.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, 0L)
        val notificationTime = inputData.getLong(KEY_NOTIFICATION_TIME, 0L)
        if (reminderId == 0L || notificationTime <= 0L) return Result.failure()

        ReminderNotifier.showReminderNotification(
            context = applicationContext,
            reminderId = reminderId,
            expectedNotificationTime = notificationTime,
        )
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_NOTIFICATION_TIME = "notification_time"

        const val EXTRA_REMINDER_ID = "com.orbit.app.extra.REMINDER_ID"
        const val EXTRA_CAPTURE_ID = "com.orbit.app.extra.CAPTURE_ID"
        const val EXTRA_TASK_ID = "com.orbit.app.extra.TASK_ID"
    }
}
