package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity

interface ReminderScheduler {
    /**
     * Schedules the reminder notification when [ReminderDeliveryPolicy] allows it.
     * Returns a scheduling token, or null when nothing was scheduled (for example a
     * past or already-delivered notification time).
     */
    fun schedule(reminder: ReminderEntity): String?

    /** Cancels every delivery path and removes a notification that is still showing. */
    fun cancel(reminderId: Long)

    fun reschedule(reminder: ReminderEntity): String? {
        cancel(reminder.id)
        return schedule(reminder)
    }

    /** Called after one path delivered, so the other path cannot ring again. */
    fun cancelAlternateDelivery(reminderId: Long, deliveredBy: ReminderDeliveryPath) = Unit
}

enum class ReminderDeliveryPath { Alarm, Worker, Reconcile }
