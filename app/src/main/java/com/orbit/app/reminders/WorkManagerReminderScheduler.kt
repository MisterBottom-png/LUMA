package com.orbit.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbit.app.data.local.entity.ReminderEntity
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: ReminderEntity): String? {
        if (reminder.id == 0L) return null
        val notificationTime = reminder.notificationTimeMillis() ?: return null
        val delay = reminderDelayMillis(notificationTime, System.currentTimeMillis())
        scheduleAlarm(reminder, notificationTime)
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(ReminderNotificationWorker.KEY_REMINDER_ID, reminder.id)
                    .putLong(
                        ReminderNotificationWorker.KEY_NOTIFICATION_TIME,
                        notificationTime,
                    )
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id.toString()
    }

    override fun cancel(reminderId: Long) {
        cancelAlarm(reminderId)
        workManager.cancelUniqueWork(uniqueWorkName(reminderId))
    }

    private fun uniqueWorkName(reminderId: Long) = "orbit_reminder_$reminderId"

    private fun scheduleAlarm(reminder: ReminderEntity, notificationTime: Long) {
        val pendingIntent = alarmIntent(
            reminderId = reminder.id,
            notificationTime = notificationTime,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            notificationTime,
            pendingIntent,
        )
    }

    private fun cancelAlarm(reminderId: Long) {
        val pendingIntent = alarmIntent(
            reminderId = reminderId,
            notificationTime = null,
            flags = PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun alarmIntent(
        reminderId: Long,
        notificationTime: Long?,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            putExtra(ReminderNotificationWorker.KEY_REMINDER_ID, reminderId)
            notificationTime?.let {
                putExtra(ReminderNotificationWorker.KEY_NOTIFICATION_TIME, it)
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            reminderNotificationRequestCode(reminderId),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal fun reminderDelayMillis(notificationTime: Long, now: Long): Long =
    (notificationTime - now).coerceAtLeast(0L)
