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
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** B5: the item page refuses a reminder time that cannot ring and says why. */
@RunWith(AndroidJUnit4::class)
class ReminderPassedTimeTest {
    private lateinit var container: OrbitContainer
    private val store = ViewModelStore()
    private val hour = 3_600_000L

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
    fun aPassedTimeIsRefusedAndTheReminderKeepsItsTime() {
        val dueAt = System.currentTimeMillis() + 2 * hour
        val (id, viewModel) = reminder(ReminderEntity(title = "Water the roses", dueAt = dueAt))

        viewModel.updateSchedule(ItemSchedule.Timed(System.currentTimeMillis() - hour))

        awaitMessage(viewModel, "That time has already passed. Choose a later time.")
        assertEquals(dueAt, runBlocking { container.reminderRepository.getById(id) }?.dueAt)
    }

    @Test
    fun aHeadsUpThatWouldBeInThePastIsRefused() {
        val dueAt = System.currentTimeMillis() + 10 * 60_000L
        val (id, viewModel) = reminder(ReminderEntity(title = "Water the roses", dueAt = dueAt))

        viewModel.updateNotificationOffset(30)

        awaitMessage(viewModel, "The heads-up would be in the past. Choose a later time or a shorter heads-up.")
        assertEquals(0L, runBlocking { container.reminderRepository.getById(id) }?.notificationOffsetMinutes)
    }

    @Test
    fun turningTheNotificationBackOnForAPassedTimeSaysItWontRing() {
        val (id, viewModel) = reminder(
            ReminderEntity(title = "Water the roses", dueAt = System.currentTimeMillis() - hour, notificationEnabled = false),
        )

        viewModel.setNotificationEnabled(true)

        awaitMessage(viewModel, "Saved. This time has passed, so it won’t ring.")
        assertEquals(true, runBlocking { container.reminderRepository.getById(id) }?.notificationEnabled)
    }

    private fun reminder(entity: ReminderEntity): Pair<Long, ItemDetailViewModel> {
        val id = runBlocking { container.reminderRepository.insert(entity) }
        val viewModel = ViewModelProvider.create(
            store,
            ItemDetailViewModel.Factory(ItemDetailType.Reminder, id, container),
        )[ItemDetailViewModel::class.java]
        return id to viewModel
    }

    private fun awaitMessage(viewModel: ItemDetailViewModel, expected: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (viewModel.uiState.value.message != expected) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) fail("message was ${viewModel.uiState.value.message}")
            Thread.sleep(20)
        }
    }
}
