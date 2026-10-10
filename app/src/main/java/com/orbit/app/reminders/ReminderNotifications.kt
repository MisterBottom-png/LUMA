package com.orbit.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.orbit.app.R

object ReminderNotifications {
    /**
     * Reminder channel. The id changed from the original `orbit_reminders` because
     * Android never lets an app raise the importance of an existing channel, and
     * reminders must appear as heads-up notifications to be noticed.
     */
    const val CHANNEL_ID = "luma_reminders"
    private const val LegacyChannelId = "orbit_reminders"

    fun createChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
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
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localizedDescription
        }
        notificationManager.createNotificationChannel(channel)
        if (notificationManager.getNotificationChannel(LegacyChannelId) != null) {
            notificationManager.deleteNotificationChannel(LegacyChannelId)
        }
    }
}
