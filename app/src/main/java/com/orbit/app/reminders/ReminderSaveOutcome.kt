package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity

/**
 * What saving a reminder actually achieved, read after the database commit.
 * Every place that saves a reminder reports one of these, so none of them can say
 * "Reminder set" while the reminder cannot reach the user.
 */
/** Declared from best to worst; see [worst]. */
enum class ReminderSaveOutcome {
    /** Saved, and its notification is on its way. */
    Saved,

    /** Saved, but its notification could not be scheduled. */
    SavedNotScheduled,

    /** Saved, but notifications are off for Tallele or for its reminder channel. */
    SavedNotificationsBlocked,
}

/** Whether Android currently lets reminders reach the user. */
data class NotificationAccess(
    val notificationsAllowed: Boolean,
    val reminderChannelEnabled: Boolean,
) {
    val canNotify: Boolean get() = notificationsAllowed && reminderChannelEnabled
}

/** Computes [ReminderSaveOutcome] for a stored reminder. */
class ReminderSaveOutcomes(
    private val reminderById: suspend (Long) -> ReminderEntity?,
    private val access: () -> NotificationAccess,
) {
    suspend fun of(reminderId: Long): ReminderSaveOutcome = outcomeFor(reminderById(reminderId), access())

    companion object {
        fun outcomeFor(reminder: ReminderEntity?, access: NotificationAccess): ReminderSaveOutcome = when {
            reminder == null -> ReminderSaveOutcome.SavedNotScheduled
            // Nothing will be shown for it anyway (for example notifications turned off on this reminder).
            !reminder.shouldScheduleNotification() -> ReminderSaveOutcome.Saved
            !access.canNotify -> ReminderSaveOutcome.SavedNotificationsBlocked
            reminder.notificationWorkId == null -> ReminderSaveOutcome.SavedNotScheduled
            else -> ReminderSaveOutcome.Saved
        }
    }
}

/** The least happy of several outcomes (entries are declared from best to worst). */
fun Iterable<ReminderSaveOutcome>.worst(): ReminderSaveOutcome? = maxByOrNull { it.ordinal }
