package com.orbit.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orbit.app.MainActivity
import com.orbit.app.OrbitApplication
import com.orbit.app.R
import com.orbit.app.data.local.OrbitDatabase

object ReminderNotifier {
    suspend fun showReminderNotification(
        context: Context,
        reminderId: Long,
        expectedNotificationTime: Long,
    ): Boolean {
        val appContext = context.applicationContext
        val reminder = (appContext as? OrbitApplication)
            ?.container
            ?.reminderRepository
            ?.getById(reminderId)
            ?: OrbitDatabase.getInstance(appContext).reminderDao().getById(reminderId)
            ?: return false

        if (!reminder.matchesScheduledNotificationTime(expectedNotificationTime)) return false

        ReminderNotifications.createChannel(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val openLumaIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderNotificationWorker.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderNotificationWorker.EXTRA_CAPTURE_ID, reminder.linkedCaptureId ?: 0L)
            putExtra(ReminderNotificationWorker.EXTRA_TASK_ID, reminder.linkedTaskId ?: 0L)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            reminderNotificationRequestCode(reminderId),
            openLumaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = reminder.title.ifBlank { "LUMA reminder" }
        val notes = reminder.notes
        val notification = NotificationCompat.Builder(
            appContext,
            ReminderNotifications.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_luma_notification)
            .setContentTitle(title)
            .setContentText(notes.ifBlank { "Tap to open LUMA" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes.ifBlank { title }))
            .setContentIntent(pendingIntent)
            .setWhen(reminder.dueAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(reminderNotificationRequestCode(reminderId), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

internal fun reminderNotificationRequestCode(reminderId: Long): Int = reminderId.hashCode()
