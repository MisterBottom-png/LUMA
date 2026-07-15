package com.orbit.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object ReminderNotifications {
    const val CHANNEL_ID = "orbit_reminders"
    const val CHANNEL_NAME = "LUMA reminders"
    const val CHANNEL_DESCRIPTION = "Reminders created in LUMA"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
