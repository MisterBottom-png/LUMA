package com.orbit.app.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.data.local.entity.ReminderEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class WorkManagerReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun usesAnExactAlarmWhenAndroidAllowsIt() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = WorkManagerReminderScheduler(context) { now }

        val token = scheduler.schedule(reminder(dueAt = now + 3_600_000L))

        assertNotNull(token)
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(now + 3_600_000L, alarm.triggerAtMs)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
    }

    @Test
    fun fallsBackToAnInexactAlarmPlusBackupWorkWhenExactAlarmsAreDenied() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = WorkManagerReminderScheduler(context) { now }

        val token = scheduler.schedule(reminder(dueAt = now + 3_600_000L))

        assertNotNull(token)
        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertTrue(alarm.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("orbit_reminder_7").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    @Test
    fun aPastReminderArmsNothing() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = WorkManagerReminderScheduler(context) { now }

        assertNull(scheduler.schedule(reminder(dueAt = now - 3_600_000L)))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("orbit_reminder_7").get()
        assertTrue(work.none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun cancelRemovesBothDeliveryPaths() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = WorkManagerReminderScheduler(context) { now }
        scheduler.schedule(reminder(dueAt = now + 3_600_000L))

        scheduler.cancel(7L)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("orbit_reminder_7").get()
        assertTrue(work.all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun reminder(dueAt: Long) = ReminderEntity(id = 7, title = "Pay rent", dueAt = dueAt)
}
