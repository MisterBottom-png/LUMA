package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity

/**
 * The single source of truth for when a reminder may ring.
 *
 * - A notification time in the past is never scheduled again, so editing, moving
 *   or restoring an old reminder cannot make it ring.
 * - A notification time that was already delivered is never scheduled again.
 * - After a restart or force-stop, a reminder that was scheduled but missed is
 *   surfaced once, and very old misses are marked handled quietly (they remain
 *   visible in Review).
 */
internal object ReminderDeliveryPolicy {
    /** Small allowance so a reminder created "now" still rings. */
    const val CreationGraceMillis: Long = 60_000L

    /** A reminder this recent is shown as a normal reminder, not as "missed". */
    const val RecentWindowMillis: Long = 15L * 60_000L

    /** Misses older than this are not notified; Review still shows them. */
    const val MissedWindowMillis: Long = 7L * 24L * 60L * 60_000L

    /** When exact alarms work, the WorkManager backup waits this long before trying. */
    const val BackupDelayMillis: Long = 2L * 60_000L

    /** At most this many missed reminders are shown individually; more become one summary. */
    const val MaxIndividualMissedNotifications: Int = 3

    /** Returns the notification time to schedule, or null when nothing may be scheduled. */
    fun scheduleTime(reminder: ReminderEntity, now: Long): Long? {
        if (!reminder.shouldScheduleNotification()) return null
        val time = reminder.notificationTimeMillis() ?: return null
        if (reminder.deliveredNotificationAt == time) return null
        if (time < now - CreationGraceMillis) return null
        return time
    }

    /** Decides what a restart/app-start reconciliation should do with one reminder. */
    fun reconcile(reminder: ReminderEntity, now: Long): ReconcileAction {
        if (!reminder.shouldScheduleNotification()) return ReconcileAction.None
        val time = reminder.notificationTimeMillis() ?: return ReconcileAction.None
        if (reminder.deliveredNotificationAt == time) return ReconcileAction.None
        if (time >= now - CreationGraceMillis) return ReconcileAction.Schedule(time)
        // Never scheduled (saved with a past time, or scheduling failed and the user
        // was told): do not ring it late; Review still lists it.
        if (reminder.notificationWorkId == null) return ReconcileAction.MarkHandled(time)
        val age = now - time
        return when {
            age <= RecentWindowMillis -> ReconcileAction.DeliverNow(time, missed = false)
            age <= MissedWindowMillis -> ReconcileAction.DeliverNow(time, missed = true)
            else -> ReconcileAction.MarkHandled(time)
        }
    }

    /** Fields whose change requires the alarm to be replaced. */
    fun schedulingKey(reminder: ReminderEntity): Triple<Long?, Boolean, Boolean> = Triple(
        reminder.notificationTimeMillis(),
        reminder.notificationEnabled,
        reminder.completedAt == null,
    )
}

internal sealed interface ReconcileAction {
    data object None : ReconcileAction
    data class Schedule(val notificationTime: Long) : ReconcileAction
    data class DeliverNow(val notificationTime: Long, val missed: Boolean) : ReconcileAction
    data class MarkHandled(val notificationTime: Long) : ReconcileAction
}
