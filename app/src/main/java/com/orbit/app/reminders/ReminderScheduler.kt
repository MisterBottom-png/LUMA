package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity

interface ReminderScheduler {
    fun schedule(reminder: ReminderEntity): String?
    fun cancel(reminderId: Long)

    fun reschedule(reminder: ReminderEntity): String? {
        cancel(reminder.id)
        return schedule(reminder)
    }
}
