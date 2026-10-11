package com.orbit.app.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ReminderBootReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager get() = WorkManager.getInstance(context)

    private val rescheduleActions = listOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        // "android.intent.action.TIME_SET"
        Intent.ACTION_TIME_CHANGED,
    )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun theManifestRoutesEveryRescheduleActionToTheReceiver() {
        rescheduleActions.forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                0,
            )
            assertTrue(
                "$action is not routed to ReminderBootReceiver",
                receivers.any { it.activityInfo.name == ReminderBootReceiver::class.java.name },
            )
        }
    }

    @Test
    fun eachActionQueuesExactlyOneReconciliation() {
        rescheduleActions.forEach { action ->
            clearWork()

            context.sendBroadcast(Intent(action).setPackage(context.packageName))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(action, 1, rescheduleWork().size)
        }
    }

    @Test
    fun repeatedBroadcastsStillLeaveOneReconciliation() {
        clearWork()

        rescheduleActions.forEach { action ->
            ReminderBootReceiver().onReceive(context, Intent(action))
        }

        assertEquals(1, rescheduleWork().size)
    }

    @Test
    fun anUnrelatedBroadcastQueuesNothing() {
        clearWork()

        ReminderBootReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        assertTrue(rescheduleWork().isEmpty())
    }

    private fun rescheduleWork() =
        workManager.getWorkInfosForUniqueWork(RescheduleWorkName).get()

    private fun clearWork() {
        workManager.cancelAllWork().result.get()
        workManager.pruneWork().result.get()
    }

    private companion object {
        const val RescheduleWorkName = "luma_reschedule_reminders"
    }
}
