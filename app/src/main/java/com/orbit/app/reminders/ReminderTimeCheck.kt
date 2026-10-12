package com.orbit.app.reminders

/** Why a reminder time the user chose cannot ring (review B5). */
enum class ReminderTimeProblem {
    /** The reminder's own time has passed. */
    TargetPassed,

    /** The time is ahead, but the heads-up before it would fall in the past. */
    AlertPassed,
}

/**
 * Checks a chosen reminder time before it is saved. The alert rings at
 * `dueAt − offset`; both use the same grace as scheduling, so a time set "now" still rings.
 */
object ReminderTimeCheck {
    fun problem(dueAt: Long, offsetMinutes: Long, now: Long): ReminderTimeProblem? {
        val earliest = now - ReminderDeliveryPolicy.CreationGraceMillis
        if (dueAt < earliest) return ReminderTimeProblem.TargetPassed
        val alertAt = reminderNotificationTimeMillis(dueAt, offsetMinutes) ?: return ReminderTimeProblem.AlertPassed
        if (alertAt < earliest) return ReminderTimeProblem.AlertPassed
        return null
    }
}
