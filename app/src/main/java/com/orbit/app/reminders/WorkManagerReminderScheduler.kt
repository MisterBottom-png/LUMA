package com.orbit.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbit.app.data.local.entity.ReminderEntity
import java.util.concurrent.TimeUnit

/**
 * Delivers reminders through one AlarmManager alarm (exact when Android allows it)
 * plus a WorkManager backup. Both paths go through [ReminderNotifier], which claims
 * delivery atomically, so whichever path runs first wins and the other is cancelled.
 */
class WorkManagerReminderScheduler(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: ReminderEntity): String? {
        if (reminder.id == 0L) return null
        val currentTime = now()
        val notificationTime = ReminderDeliveryPolicy.scheduleTime(reminder, currentTime)
        if (notificationTime == null) {
            cancelDeliveryPaths(reminder.id)
            return null
        }
        val exact = ReminderCapabilities.canScheduleExactAlarms(appContext)
        scheduleAlarm(reminder.id, notificationTime, exact)
        val backupDelay = reminderDelayMillis(notificationTime, currentTime) +
            if (exact) ReminderDeliveryPolicy.BackupDelayMillis else 0L
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(backupDelay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(ReminderNotificationWorker.KEY_REMINDER_ID, reminder.id)
                    .putLong(ReminderNotificationWorker.KEY_NOTIFICATION_TIME, notificationTime)
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
        cancelDeliveryPaths(reminderId)
        runCatching {
            NotificationManagerCompat.from(appContext)
                .cancel(reminderNotificationRequestCode(reminderId))
        }
    }

    override fun cancelAlternateDelivery(reminderId: Long, deliveredBy: ReminderDeliveryPath) {
        when (deliveredBy) {
            ReminderDeliveryPath.Alarm -> workManager.cancelUniqueWork(uniqueWorkName(reminderId))
            ReminderDeliveryPath.Worker -> cancelAlarm(reminderId)
            ReminderDeliveryPath.Reconcile -> cancelDeliveryPaths(reminderId)
        }
    }

    private fun cancelDeliveryPaths(reminderId: Long) {
        cancelAlarm(reminderId)
        workManager.cancelUniqueWork(uniqueWorkName(reminderId))
    }

    private fun uniqueWorkName(reminderId: Long) = "orbit_reminder_$reminderId"

    private fun scheduleAlarm(reminderId: Long, notificationTime: Long, exact: Boolean) {
        val pendingIntent = alarmIntent(
            reminderId = reminderId,
            notificationTime = notificationTime,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent,
                )
                return
            } catch (_: SecurityException) {
                // The exact-alarm permission was revoked between the check and the call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notificationTime, pendingIntent)
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

/** Whether this device lets LUMA use exact alarms right now. */
internal fun AlarmManager.canUseExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
