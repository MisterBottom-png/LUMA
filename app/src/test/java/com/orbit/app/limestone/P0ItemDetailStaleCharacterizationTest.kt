package com.orbit.app.limestone

import android.Manifest
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.reminders.ReminderActionReceiver
import com.orbit.app.reminders.handleReminderAction
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.screens.item.ItemDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * B10 ("an open item screen shows stale data") — P0 result: REPRODUCED.
 *
 * [ItemDetailViewModel] reads its item once (`load()` with `getById`) and only reloads
 * after its own actions. A change made elsewhere while the screen is open — Done on the
 * reminder notification, or any other writer of the row — is stored, but the open screen
 * keeps showing the old values.
 *
 * Flips when B10 (live detail) lands in P4: each "still shows the old value" assertion
 * must then expect the new value.
 */
@RunWith(AndroidJUnit4::class)
class P0ItemDetailStaleCharacterizationTest {
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
    fun doneFromTheNotificationIsStoredButTheOpenReminderScreenStillShowsItOpen() {
        val now = System.currentTimeMillis()
        val reminderId = runBlocking {
            container.reminderRepository.insert(ReminderEntity(title = "Take out the recycling", dueAt = now + 3_600_000))
        }
        val viewModel = viewModel(ItemDetailType.Reminder, reminderId)
        idleMainUntil { !viewModel.uiState.value.isLoading }
        assertFalse(viewModel.uiState.value.isComplete)

        // Exactly what the notification's Done button runs.
        runBlocking {
            handleReminderAction(container.reminderRepository, ReminderActionReceiver.ACTION_DONE, reminderId, now)
        }
        idleMainFor(500)

        val stored = runBlocking { container.reminderRepository.getById(reminderId) }
        assertNotNull("the row is completed", stored?.completedAt)
        // Stale today (B10): the open screen still offers the reminder as not done.
        assertFalse("open screen still shows it as not complete", viewModel.uiState.value.isComplete)
    }

    @Test
    fun aTaskRenamedElsewhereKeepsItsOldTitleOnTheOpenTaskScreen() {
        val taskId = runBlocking { container.taskRepository.insert(TaskEntity(title = "Buy milk")) }
        val viewModel = viewModel(ItemDetailType.Task, taskId)
        idleMainUntil { !viewModel.uiState.value.isLoading }
        assertEquals("Buy milk", viewModel.uiState.value.title)

        runBlocking {
            val task = requireNotNull(container.taskRepository.getById(taskId))
            container.taskRepository.update(task.copy(title = "Buy oat milk", updatedAt = System.currentTimeMillis()))
        }
        idleMainFor(500)

        assertEquals("Buy oat milk", runBlocking { container.taskRepository.getById(taskId)?.title })
        // Stale today (B10).
        assertEquals("open screen still shows the old title", "Buy milk", viewModel.uiState.value.title)
    }

    private fun viewModel(type: ItemDetailType, id: Long): ItemDetailViewModel = ViewModelProvider.create(
        store,
        ItemDetailViewModel.Factory(type, id, container),
    )[ItemDetailViewModel::class.java]
}
