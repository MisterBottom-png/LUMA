package com.orbit.app.ui.screens.item

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Giving a missed reminder a new time in its own screen answers the Needs you card. */
@RunWith(AndroidJUnit4::class)
class MissedReminderRescheduleTest {
    private lateinit var container: OrbitContainer
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        container = OrbitContainer(application)
        runBlocking { withContext(Dispatchers.IO) { container.database.clearAllTables() } }
    }

    @After
    fun tearDown() {
        store.clear()
        container.applicationScope.cancel()
    }

    @Test
    fun aNewFutureTimeTurnsTimePassedBackIntoReminderSet() {
        val now = System.currentTimeMillis()
        val (captureId, reminderId) = runBlocking {
            val captureId = container.captureRepository.insert(
                CaptureEntity(
                    rawText = "Call the plumber at 8:00",
                    status = CaptureStatus.Processed,
                    outcome = CaptureOutcome.NeedsYou,
                    outcomeReason = CaptureOutcomeReason.TimePassed,
                    decidedAt = now - 60_000,
                ),
            )
            val reminderId = container.reminderRepository.insert(
                ReminderEntity(title = "Call the plumber", dueAt = now - 3_600_000, linkedCaptureId = captureId),
            )
            container.captureRepository.update(
                requireNotNull(container.captureRepository.getById(captureId)).copy(linkedItemId = reminderId),
            )
            captureId to reminderId
        }
        val viewModel = ViewModelProvider.create(
            store,
            ItemDetailViewModel.Factory(ItemDetailType.Reminder, reminderId, container),
        )[ItemDetailViewModel::class.java]

        viewModel.updateSchedule(ItemSchedule.Timed(now + 86_400_000))

        val deadline = System.currentTimeMillis() + 10_000
        var outcome: CaptureOutcome? = null
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            outcome = runBlocking { container.captureRepository.getById(captureId)?.outcome }
            if (outcome == CaptureOutcome.ReminderSet) break
            Thread.sleep(20)
        }
        val reason = runBlocking { container.captureRepository.getById(captureId)?.outcomeReason }
        assertEquals("reason=$reason", CaptureOutcome.ReminderSet, outcome)
    }
}
