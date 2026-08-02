package com.orbit.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.orbit.app.R

object ReminderNotifications {
    const val CHANNEL_ID = "orbit_reminders"

    fun createChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val localizedName = context.getText(R.string.reminder_notification_channel_name)
        val localizedDescription = context.getString(
            R.string.reminder_notification_channel_description,
        )
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)?.apply {
            name = localizedName
            description = localizedDescription
        } ?: NotificationChannel(
            CHANNEL_ID,
            localizedName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localizedDescription
        }
        notificationManager.createNotificationChannel(channel)
    }
}
