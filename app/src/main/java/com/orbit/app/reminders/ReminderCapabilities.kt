package com.orbit.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What Android currently allows LUMA to do with reminders. */
data class ReminderCapabilityStatus(
    val notificationsAllowed: Boolean,
    val reminderChannelEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val exactAlarmsAdjustable: Boolean,
    val lastBlockedDeliveryAt: Long?,
) {
    /** Reminders can reach the user at all. */
    val canNotify: Boolean get() = notificationsAllowed && reminderChannelEnabled

    val state: ReminderCapabilityState
        get() = when {
            !canNotify -> ReminderCapabilityState.Blocked
            !exactAlarmsAllowed -> ReminderCapabilityState.MayBeLate
            else -> ReminderCapabilityState.Ready
        }
}

enum class ReminderCapabilityState { Ready, MayBeLate, Blocked }

object ReminderCapabilities {
    private const val PreferencesName = "luma_reminder_delivery"
    private const val KeyLastBlocked = "last_blocked_delivery_at"

    fun status(context: Context): ReminderCapabilityStatus {
        val appContext = context.applicationContext
        return ReminderCapabilityStatus(
            notificationsAllowed = notificationsAllowed(appContext),
            reminderChannelEnabled = reminderChannelEnabled(appContext),
            exactAlarmsAllowed = canScheduleExactAlarms(appContext),
            exactAlarmsAdjustable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            lastBlockedDeliveryAt = preferences(appContext)
                .getLong(KeyLastBlocked, 0L)
                .takeIf { it > 0L },
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun reminderChannelEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canUseExactAlarms() ?: false

    internal fun recordBlockedDelivery(context: Context, at: Long) {
        preferences(context).edit().putLong(KeyLastBlocked, at).apply()
    }

    internal fun clearBlockedDelivery(context: Context) {
        preferences(context).edit().remove(KeyLastBlocked).apply()
    }

    /** Opens the screen where the user can turn LUMA notifications back on. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the "Alarms & reminders" permission screen, where Android supports it. */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
