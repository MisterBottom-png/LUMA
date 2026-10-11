package com.orbit.app.reminders

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Settings health row: every combination of what Android allows maps to one line
 * and at most one button, and only "can't ring with a reminder due soon" is red.
 */
@RunWith(AndroidJUnit4::class)
class ReminderCapabilityStateTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun everyFlagCombinationGivesOneRowStateAndOneButton() {
        val flags = listOf(false, true)
        var checked = 0
        for (notifications in flags) for (channel in flags) for (exact in flags) for (adjustable in flags) {
            for (restricted in flags) for (aggressive in flags) {
                val status = status(notifications, channel, exact, adjustable, restricted, aggressive)
                val health = status.health(nextNotificationAt = null, now = now)
                val (issue, fix) = expected(notifications, channel, exact, adjustable, restricted, aggressive)
                val label = "n=$notifications c=$channel e=$exact a=$adjustable r=$restricted b=$aggressive"
                assertEquals(label, issue, health.issue)
                assertEquals(label, fix, health.fix)
                assertFalse(label, health.urgent)
                checked++
            }
        }
        assertEquals(64, checked)
    }

    @Test
    fun allClearSaysNothingIsBlockingAndOffersNoButton() {
        val health = status().health(nextNotificationAt = now + hour, now = now)
        assertEquals(ReminderHealth(ReminderHealthIssue.None, fix = null, urgent = false), health)
    }

    @Test
    fun blockedIsRedOnlyWhenAReminderIsDueWithinADay() {
        val off = status(notifications = false)
        assertTrue(off.health(nextNotificationAt = now + hour, now = now).urgent)
        assertTrue(off.health(nextNotificationAt = now + 24 * hour, now = now).urgent)
        assertFalse(off.health(nextNotificationAt = now + 24 * hour + 1, now = now).urgent)
        assertFalse(off.health(nextNotificationAt = null, now = now).urgent)
        // A time already gone is not "due soon".
        assertFalse(off.health(nextNotificationAt = now - hour, now = now).urgent)

        val muted = status(channel = false)
        assertTrue(muted.health(nextNotificationAt = now + hour, now = now).urgent)
    }

    @Test
    fun lateAndBatteryHintsAreNeverRed() {
        val late = status(exact = false)
        val stopped = status(restricted = true, aggressive = true)
        assertEquals(ReminderHealthIssue.MayBeLate, late.health(now + hour, now).issue)
        assertEquals(ReminderHealthIssue.MayBeStopped, stopped.health(now + hour, now).issue)
        assertFalse(late.health(nextNotificationAt = now + hour, now = now).urgent)
        assertFalse(stopped.health(nextNotificationAt = now + hour, now = now).urgent)
    }

    @Test
    fun eachIssueOpensItsOwnScreen() {
        val context: Context = ApplicationProvider.getApplicationContext()

        val notifications = ReminderCapabilities.fixIntent(context, ReminderHealthIssue.NotificationsOff)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications?.action)

        val channel = ReminderCapabilities.fixIntent(context, ReminderHealthIssue.ChannelOff)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel?.action)
        assertEquals(ReminderNotifications.CHANNEL_ID, channel?.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals(context.packageName, channel?.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val late = ReminderCapabilities.fixIntent(context, ReminderHealthIssue.MayBeLate)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, late?.action)

        // Robolectric is not a Samsung phone: the app's own battery request.
        val battery = ReminderCapabilities.fixIntent(context, ReminderHealthIssue.MayBeStopped)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery?.action)

        assertNull(ReminderCapabilities.fixIntent(context, ReminderHealthIssue.None))
    }

    @Test
    fun samsungStepsShowOnlyWhenItsScreenCannotBeOpened() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertTrue(ReminderCapabilities.needsSamsungSteps(context, manufacturer = "samsung"))
        assertFalse(ReminderCapabilities.needsSamsungSteps(context, manufacturer = "Xiaomi"))
        assertFalse(ReminderCapabilities.needsSamsungSteps(context, manufacturer = null))
    }

    @Test
    fun nextReminderSkipsDoneDeliveredSilentAndPastOnes() = runBlocking {
        val database = inMemoryOrbitDatabase()
        try {
            val dao = database.reminderDao()
            assertNull(dao.nextUndeliveredNotificationAt(now))

            dao.insert(ReminderEntity(title = "past", dueAt = now - hour))
            dao.insert(ReminderEntity(title = "done", dueAt = now + hour, completedAt = now - 1))
            dao.insert(ReminderEntity(title = "silent", dueAt = now + hour, notificationEnabled = false))
            dao.insert(
                ReminderEntity(title = "rung", dueAt = now + 2 * hour, deliveredNotificationAt = now + 2 * hour),
            )
            dao.insert(ReminderEntity(title = "later", dueAt = now + 30 * hour))
            assertEquals(now + 30 * hour, dao.nextUndeliveredNotificationAt(now))

            // The notification time counts, not the due time: early offsets and snoozes.
            dao.insert(ReminderEntity(title = "early", dueAt = now + 10 * hour, notificationOffsetMinutes = 60))
            assertEquals(now + 9 * hour, dao.nextUndeliveredNotificationAt(now))
            dao.insert(ReminderEntity(title = "snoozed", dueAt = now - hour, snoozedUntil = now + 3 * hour))
            assertEquals(now + 3 * hour, dao.nextUndeliveredNotificationAt(now))
        } finally {
            database.close()
        }
    }

    /** The rule written out once more, independently of the code under test. */
    private fun expected(
        notifications: Boolean,
        channel: Boolean,
        exact: Boolean,
        adjustable: Boolean,
        restricted: Boolean,
        aggressive: Boolean,
    ): Pair<ReminderHealthIssue, ReminderHealthFix?> = when {
        !notifications -> ReminderHealthIssue.NotificationsOff to ReminderHealthFix.TurnOn
        !channel -> ReminderHealthIssue.ChannelOff to ReminderHealthFix.TurnOn
        !exact -> ReminderHealthIssue.MayBeLate to (if (adjustable) ReminderHealthFix.Allow else null)
        restricted && aggressive -> ReminderHealthIssue.MayBeStopped to ReminderHealthFix.Check
        else -> ReminderHealthIssue.None to null
    }

    private fun status(
        notifications: Boolean = true,
        channel: Boolean = true,
        exact: Boolean = true,
        adjustable: Boolean = true,
        restricted: Boolean = false,
        aggressive: Boolean = false,
    ) = ReminderCapabilityStatus(
        notificationsAllowed = notifications,
        reminderChannelEnabled = channel,
        exactAlarmsAllowed = exact,
        exactAlarmsAdjustable = adjustable,
        lastBlockedDeliveryAt = null,
        batteryRestricted = restricted,
        aggressiveBatteryBrand = aggressive,
    )
}
