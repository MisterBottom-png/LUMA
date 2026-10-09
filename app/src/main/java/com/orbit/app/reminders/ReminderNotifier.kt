package com.orbit.app.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.orbit.app.MainActivity
import com.orbit.app.OrbitApplication
import com.orbit.app.R
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.domain.model.uses24HourClock
import com.orbit.app.ui.time.OrbitTimeFormat
import kotlinx.coroutines.flow.first

enum class ReminderDeliveryOutcome { Shown, AlreadyHandled, Blocked, Missing }

object ReminderNotifier {
    /**
     * Shows one reminder notification at most once per notification time. Both the
     * alarm and the backup worker call this; the database claim decides the winner.
     */
    suspend fun showReminderNotification(
        context: Context,
        reminderId: Long,
        expectedNotificationTime: Long,
        deliveredBy: ReminderDeliveryPath = ReminderDeliveryPath.Worker,
        missed: Boolean = false,
    ): ReminderDeliveryOutcome {
        val appContext = context.applicationContext
        val dao = reminderDao(appContext)
        val reminder = dao.getById(reminderId) ?: return ReminderDeliveryOutcome.Missing
        if (!reminder.matchesScheduledNotificationTime(expectedNotificationTime)) {
            return ReminderDeliveryOutcome.AlreadyHandled
        }

        ReminderNotifications.createChannel(appContext)
        if (!ReminderCapabilities.notificationsAllowed(appContext) ||
            !ReminderCapabilities.reminderChannelEnabled(appContext)
        ) {
            // Do not claim: if the user turns notifications back on, a later
            // reconciliation can still surface this reminder as missed.
            ReminderCapabilities.recordBlockedDelivery(appContext, System.currentTimeMillis())
            return ReminderDeliveryOutcome.Blocked
        }
        if (dao.claimDelivery(reminderId, expectedNotificationTime) != 1) {
            return ReminderDeliveryOutcome.AlreadyHandled
        }

        val notification = buildReminderNotification(
            context = appContext,
            reminder = reminder,
            notificationTime = expectedNotificationTime,
            missed = missed,
        )
        return try {
            NotificationManagerCompat.from(appContext)
                .notify(reminderNotificationRequestCode(reminderId), notification)
            ReminderCapabilities.clearBlockedDelivery(appContext)
            scheduler(appContext).cancelAlternateDelivery(reminderId, deliveredBy)
            ReminderDeliveryOutcome.Shown
        } catch (_: SecurityException) {
            dao.releaseDelivery(reminderId, expectedNotificationTime)
            ReminderCapabilities.recordBlockedDelivery(appContext, System.currentTimeMillis())
            ReminderDeliveryOutcome.Blocked
        }
    }

    /**
     * Shows one calm summary instead of a burst of notifications when several
     * reminders were missed while the phone was off.
     */
    suspend fun showMissedSummary(
        context: Context,
        missed: List<Pair<Long, Long>>,
    ): ReminderDeliveryOutcome {
        val appContext = context.applicationContext
        if (missed.isEmpty()) return ReminderDeliveryOutcome.AlreadyHandled
        ReminderNotifications.createChannel(appContext)
        if (!ReminderCapabilities.notificationsAllowed(appContext) ||
            !ReminderCapabilities.reminderChannelEnabled(appContext)
        ) {
            ReminderCapabilities.recordBlockedDelivery(appContext, System.currentTimeMillis())
            return ReminderDeliveryOutcome.Blocked
        }
        val dao = reminderDao(appContext)
        val claimed = missed.filter { (id, time) -> dao.claimDelivery(id, time) == 1 }
        if (claimed.isEmpty()) return ReminderDeliveryOutcome.AlreadyHandled

        val openReviewIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_REVIEW, true)
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            MissedSummaryRequestCode,
            openReviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = appContext.resources.getQuantityString(
            R.plurals.reminder_notification_missed_summary_title,
            claimed.size,
            claimed.size,
        )
        val notification = NotificationCompat.Builder(appContext, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_luma_notification)
            .setContentTitle(title)
            .setContentText(appContext.getString(R.string.reminder_notification_missed_summary_body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(appContext).notify(MissedSummaryRequestCode, notification)
            claimed.forEach { (id, _) ->
                scheduler(appContext).cancelAlternateDelivery(id, ReminderDeliveryPath.Reconcile)
            }
            ReminderDeliveryOutcome.Shown
        } catch (_: SecurityException) {
            claimed.forEach { (id, time) -> dao.releaseDelivery(id, time) }
            ReminderCapabilities.recordBlockedDelivery(appContext, System.currentTimeMillis())
            ReminderDeliveryOutcome.Blocked
        }
    }

    private suspend fun buildReminderNotification(
        context: Context,
        reminder: ReminderEntity,
        notificationTime: Long,
        missed: Boolean,
    ): android.app.Notification {
        val requestCode = reminderNotificationRequestCode(reminder.id)
        val openLumaIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderNotificationWorker.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderNotificationWorker.EXTRA_CAPTURE_ID, reminder.linkedCaptureId ?: 0L)
            putExtra(ReminderNotificationWorker.EXTRA_TASK_ID, reminder.linkedTaskId ?: 0L)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openLumaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = reminder.title.ifBlank {
            context.getString(R.string.reminder_notification_fallback_title)
        }
        val body = when {
            missed -> context.getString(
                R.string.reminder_notification_missed_body,
                timeFormat(context).formatShortDateTime(reminder.dueAt),
            )
            reminder.notes.isNotBlank() -> reminder.notes
            else -> context.getString(R.string.reminder_notification_open_luma)
        }
        return NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_luma_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setWhen(reminder.dueAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                context.getString(R.string.reminder_notification_action_done),
                ReminderActionReceiver.pendingIntent(
                    context,
                    ReminderActionReceiver.ACTION_DONE,
                    reminder.id,
                    notificationTime,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.reminder_notification_action_snooze),
                ReminderActionReceiver.pendingIntent(
                    context,
                    ReminderActionReceiver.ACTION_SNOOZE,
                    reminder.id,
                    notificationTime,
                ),
            )
            .build()
    }

    private suspend fun timeFormat(context: Context): OrbitTimeFormat {
        val mode = (context as? OrbitApplication ?: context.applicationContext as? OrbitApplication)
            ?.container
            ?.appSettingsRepository
            ?.settings
            ?.first()
            ?.timeFormatMode
        val deviceUses24Hour = DateFormat.is24HourFormat(context)
        return OrbitTimeFormat(
            uses24HourClock = mode?.uses24HourClock(deviceUses24Hour) ?: deviceUses24Hour,
            locale = context.resources.configuration.locales[0] ?: java.util.Locale.ENGLISH,
        )
    }

    internal fun reminderDao(context: Context): ReminderDao =
        (context.applicationContext as? OrbitApplication)?.container?.database?.reminderDao()
            ?: OrbitDatabase.getInstance(context.applicationContext).reminderDao()

    internal fun scheduler(context: Context): ReminderScheduler =
        (context.applicationContext as? OrbitApplication)?.container?.reminderScheduler
            ?: WorkManagerReminderScheduler(context.applicationContext)

    const val EXTRA_OPEN_REVIEW = "com.orbit.app.extra.OPEN_REVIEW"
    private const val MissedSummaryRequestCode = 0x4C554D41
}

internal fun ReminderEntity.currentNotificationRequestCode(
    expectedNotificationTime: Long,
    requestReminderId: Long = id,
): Int? = reminderNotificationRequestCode(requestReminderId)
    .takeIf { matchesScheduledNotificationTime(expectedNotificationTime) }

internal fun reminderNotificationRequestCode(reminderId: Long): Int = reminderId.hashCode()
