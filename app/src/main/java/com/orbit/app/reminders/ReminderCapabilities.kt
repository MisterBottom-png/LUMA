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

/** What the Settings health row says. Blocked splits in two: each has its own fix screen. */
enum class ReminderHealthIssue { None, NotificationsOff, ChannelOff, MayBeLate, MayBeStopped }

/** The one button the health row offers, if any. */
enum class ReminderHealthFix { TurnOn, Allow, Check }

/**
 * One line of text, at most one fix, and whether the text is red. Red only when reminders
 * cannot ring at all and one is due within [ReminderHealthUrgentWindowMillis]; the late
 * and battery hints are never red.
 */
data class ReminderHealth(
    val issue: ReminderHealthIssue,
    val fix: ReminderHealthFix?,
    val urgent: Boolean,
)

const val ReminderHealthUrgentWindowMillis: Long = 24L * 60 * 60 * 1000

/**
 * Maps what Android allows to the health row. [nextNotificationAt] is the next reminder
 * that has not rung yet (see ReminderDao.nextUndeliveredNotificationAt), or null.
 */
fun ReminderCapabilityStatus.health(nextNotificationAt: Long?, now: Long): ReminderHealth {
    val issue = when (state) {
        ReminderCapabilityState.Blocked ->
            if (!notificationsAllowed) ReminderHealthIssue.NotificationsOff else ReminderHealthIssue.ChannelOff
        ReminderCapabilityState.MayBeLate -> ReminderHealthIssue.MayBeLate
        ReminderCapabilityState.MayBeStopped -> ReminderHealthIssue.MayBeStopped
        ReminderCapabilityState.Ready -> ReminderHealthIssue.None
    }
    val fix = when (issue) {
        ReminderHealthIssue.NotificationsOff, ReminderHealthIssue.ChannelOff -> ReminderHealthFix.TurnOn
        // Below Android 12 there is no "Alarms & reminders" screen to send the user to.
        ReminderHealthIssue.MayBeLate -> ReminderHealthFix.Allow.takeIf { exactAlarmsAdjustable }
        ReminderHealthIssue.MayBeStopped -> ReminderHealthFix.Check
        ReminderHealthIssue.None -> null
    }
    val urgent = state == ReminderCapabilityState.Blocked &&
        nextNotificationAt != null &&
        nextNotificationAt > now &&
        nextNotificationAt - now <= ReminderHealthUrgentWindowMillis
    return ReminderHealth(issue = issue, fix = fix, urgent = urgent)
}

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

    /**
     * What decides whether a reminder can ring: notifications, the reminder channel and
     * exact alarms. When this changes while the app is away, reminders are reconciled
     * on return (review B6).
     */
    fun deliveryKey(context: Context): BooleanArray {
        val appContext = context.applicationContext
        return booleanArrayOf(
            notificationsAllowed(appContext),
            reminderChannelEnabled(appContext),
            canScheduleExactAlarms(appContext),
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

    /**
     * On a Samsung phone whose "Never sleeping apps" list cannot be opened directly, the
     * health row spells out the path instead.
     */
    fun needsSamsungSteps(context: Context, manufacturer: String? = Build.MANUFACTURER): Boolean =
        manufacturer?.trim()?.lowercase() == "samsung" &&
            context.applicationContext.packageManager.resolveActivity(samsungNeverSleepingIntent(), 0) == null

    /** Opens the system screen for the Reminders channel, where its sound can be turned back on. */
    fun channelSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The screen that fixes [issue], or null when there is nothing to fix or nowhere to go. */
    fun fixIntent(context: Context, issue: ReminderHealthIssue): Intent? = when (issue) {
        ReminderHealthIssue.NotificationsOff -> notificationSettingsIntent(context)
        ReminderHealthIssue.ChannelOff -> channelSettingsIntent(context)
        ReminderHealthIssue.MayBeLate -> exactAlarmSettingsIntent(context)
        ReminderHealthIssue.MayBeStopped -> batteryFixIntent(context)
        ReminderHealthIssue.None -> null
    }

    private const val SamsungNeverSleepingAction = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
    private const val SamsungDeviceCarePackage = "com.samsung.android.lool"
    private const val SamsungActivityTypeExtra = "activity_type"
    private const val SamsungNeverSleepingActivityType = 2

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
