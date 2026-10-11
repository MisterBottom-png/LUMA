package com.orbit.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
    /** Android may restrict LUMA in the background to save battery. */
    val batteryRestricted: Boolean = false,
    /** The phone's maker is known to stop background apps more than Android itself does. */
    val aggressiveBatteryBrand: Boolean = false,
) {
    /** Reminders can reach the user at all. */
    val canNotify: Boolean get() = notificationsAllowed && reminderChannelEnabled

    val state: ReminderCapabilityState
        get() = when {
            !canNotify -> ReminderCapabilityState.Blocked
            !exactAlarmsAllowed -> ReminderCapabilityState.MayBeLate
            // Plain battery optimisation on other phones is fine: exact alarms fire
            // through Doze. Only brands with their own app killer get the hint.
            batteryRestricted && aggressiveBatteryBrand -> ReminderCapabilityState.MayBeStopped
            else -> ReminderCapabilityState.Ready
        }
}

/**
 * [MayBeStopped] is a hint, not a failure: the phone's own battery manager may stop
 * LUMA in the background, which Android cannot report.
 */
enum class ReminderCapabilityState { Ready, MayBeLate, MayBeStopped, Blocked }

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
            batteryRestricted = batteryRestricted(appContext),
            aggressiveBatteryBrand = isAggressiveBatteryBrand(Build.MANUFACTURER),
        )
    }

    /** Makers whose own battery managers are known to stop background apps. */
    internal val AggressiveBatteryBrands: Set<String> = setOf(
        "samsung",
        "xiaomi",
        "huawei",
        "oneplus",
        "oppo",
        "vivo",
        "realme",
    )

    internal fun isAggressiveBatteryBrand(manufacturer: String?): Boolean =
        manufacturer?.trim()?.lowercase() in AggressiveBatteryBrands

    /** True when Android applies battery optimisation to LUMA. */
    fun batteryRestricted(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return !manager.isIgnoringBatteryOptimizations(context.packageName)
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

    /** Asks Android to stop optimising LUMA's battery use. */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Samsung's "Never sleeping apps" list (Battery > Background usage limits). Not
     * guaranteed on every One UI version: use [batteryFixIntent], which checks it first.
     */
    fun samsungNeverSleepingIntent(): Intent =
        Intent(SamsungNeverSleepingAction)
            .setPackage(SamsungDeviceCarePackage)
            .putExtra(SamsungActivityTypeExtra, SamsungNeverSleepingActivityType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The screen that best lifts a battery restriction: Samsung's own list when this
     * phone can open it, otherwise the app's battery optimisation request.
     */
    fun batteryFixIntent(context: Context, manufacturer: String? = Build.MANUFACTURER): Intent {
        val appContext = context.applicationContext
        if (manufacturer?.trim()?.lowercase() == "samsung") {
            val samsung = samsungNeverSleepingIntent()
            if (appContext.packageManager.resolveActivity(samsung, 0) != null) return samsung
        }
        return batteryOptimizationIntent(appContext)
    }

    private const val SamsungNeverSleepingAction = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
    private const val SamsungDeviceCarePackage = "com.samsung.android.lool"
    private const val SamsungActivityTypeExtra = "activity_type"
    private const val SamsungNeverSleepingActivityType = 2

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
