package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderDeliveryPolicyTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 24L * 60L * minute

    @Test
    fun futureReminderIsScheduledAtItsNotificationTime() {
        val reminder = reminder(dueAt = now + 30 * minute, offset = 10)
        assertEquals(now + 20 * minute, ReminderDeliveryPolicy.scheduleTime(reminder, now))
    }

    @Test
    fun pastReminderIsNeverScheduledAgain() {
        assertNull(ReminderDeliveryPolicy.scheduleTime(reminder(dueAt = now - 5 * minute), now))
        assertNull(ReminderDeliveryPolicy.scheduleTime(reminder(dueAt = now - 3 * day), now))
    }

    @Test
    fun reminderCreatedForThisVeryMinuteStillRings() {
        val reminder = reminder(dueAt = now - 30_000L)
        assertEquals(now - 30_000L, ReminderDeliveryPolicy.scheduleTime(reminder, now))
    }

    @Test
    fun deliveredNotificationTimeIsNotScheduledAgain() {
        val time = now + 10 * minute
        val reminder = reminder(dueAt = time).copy(deliveredNotificationAt = time)
        assertNull(ReminderDeliveryPolicy.scheduleTime(reminder, now))
    }

    @Test
    fun snoozeReplacesTheNotificationTimeEvenAfterDelivery() {
        val due = now - 2 * minute
        val reminder = reminder(dueAt = due).copy(deliveredNotificationAt = due, snoozedUntil = now + 15 * minute)
        assertEquals(now + 15 * minute, ReminderDeliveryPolicy.scheduleTime(reminder, now))
    }

    @Test
    fun completedOrDisabledRemindersAreNotScheduled() {
        assertNull(ReminderDeliveryPolicy.scheduleTime(reminder(now + day).copy(completedAt = now), now))
        assertNull(ReminderDeliveryPolicy.scheduleTime(reminder(now + day).copy(notificationEnabled = false), now))
    }

    @Test
    fun reconcileSchedulesFutureAndSurfacesRecentAndMissedReminders() {
        assertEquals(
            ReconcileAction.Schedule(now + day),
            ReminderDeliveryPolicy.reconcile(reminder(now + day).scheduled(), now),
        )
        assertEquals(
            ReconcileAction.DeliverNow(now - 5 * minute, missed = false),
            ReminderDeliveryPolicy.reconcile(reminder(now - 5 * minute).scheduled(), now),
        )
        assertEquals(
            ReconcileAction.DeliverNow(now - 2 * day, missed = true),
            ReminderDeliveryPolicy.reconcile(reminder(now - 2 * day).scheduled(), now),
        )
        assertEquals(
            ReconcileAction.MarkHandled(now - 10 * day),
            ReminderDeliveryPolicy.reconcile(reminder(now - 10 * day).scheduled(), now),
        )
    }

    @Test
    fun reconcileNeverRingsAReminderThatWasNeverScheduled() {
        assertEquals(
            ReconcileAction.MarkHandled(now - 2 * day),
            ReminderDeliveryPolicy.reconcile(reminder(now - 2 * day), now),
        )
    }

    @Test
    fun reconcileIgnoresDeliveredCompletedAndDisabledReminders() {
        val time = now - day
        assertEquals(
            ReconcileAction.None,
            ReminderDeliveryPolicy.reconcile(reminder(time).scheduled().copy(deliveredNotificationAt = time), now),
        )
        assertEquals(
            ReconcileAction.None,
            ReminderDeliveryPolicy.reconcile(reminder(time).scheduled().copy(completedAt = now), now),
        )
        assertEquals(
            ReconcileAction.None,
            ReminderDeliveryPolicy.reconcile(reminder(time).scheduled().copy(notificationEnabled = false), now),
        )
    }

    @Test
    fun schedulingKeyIgnoresTitleSpaceAndNotes() {
        val base = reminder(now + day)
        val moved = base.copy(title = "Renamed", spaceId = 9, notes = "more", updatedAt = now)
        assertEquals(ReminderDeliveryPolicy.schedulingKey(base), ReminderDeliveryPolicy.schedulingKey(moved))
    }

    private fun reminder(dueAt: Long, offset: Long = 0) = ReminderEntity(
        id = 1,
        title = "Call the dentist",
        dueAt = dueAt,
        notificationOffsetMinutes = offset,
    )

    private fun ReminderEntity.scheduled() = copy(notificationWorkId = "work")
}
