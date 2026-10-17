package com.orbit.app.limestone

import android.Manifest
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.screens.settings.LocalDataToolsMessage
import com.orbit.app.ui.screens.settings.LocalDataToolsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * B5 baseline: what Settings > Reset ([LocalDataToolsViewModel.resetAllData]) removes and
 * what it keeps today.
 *
 * Pins today's narrow Reset. P5 (B5, Erase everything) must turn every KEPT assertion
 * into REMOVED.
 *
 * Not covered here: the Gemini API key. It lives in the Android Keystore
 * (`AndroidKeystoreGeminiApiKeyStore`), which Robolectric does not provide; by reading,
 * Reset does not touch it either.
 */
@RunWith(AndroidJUnit4::class)
class P0ResetScopeCharacterizationTest {
    private lateinit var application: Application
    private lateinit var container: OrbitContainer
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        container = OrbitContainer(application)
        runBlocking {
            withContext(Dispatchers.IO) { container.database.clearAllTables() }
            // The settings store outlives one test in this JVM.
            container.appSettingsRepository.reset()
        }
    }

    @After
    fun tearDown() {
        store.clear()
        runBlocking { container.appSettingsRepository.reset() }
        container.applicationScope.cancel()
    }

    @Test
    fun resetRemovesItemsButKeepsLearningMemoryAndSettings() {
        val now = System.currentTimeMillis()
        val reminderId = runBlocking { seed(now) }
        assertTrue(
            "the reminder was armed before Reset",
            reminderWork(reminderId).any { it.state == WorkInfo.State.ENQUEUED },
        )
        val settingsBefore = runBlocking { container.appSettingsRepository.settings.first() }

        val viewModel = ViewModelProvider.create(
            store,
            LocalDataToolsViewModel.Factory(container),
        )[LocalDataToolsViewModel::class.java]
        viewModel.resetAllData()
        idleMainUntil { viewModel.uiState.value.restoreMessage != null || viewModel.uiState.value.errorMessage != null }
        assertEquals(false, viewModel.uiState.value.isResetting)
        assertEquals(LocalDataToolsMessage.ResetCompleted, viewModel.uiState.value.restoreMessage)

        runBlocking {
            val db = container.database
            // REMOVED today.
            assertTrue("spaces", db.spaceDao().observeAll().first().isEmpty())
            assertTrue("captures", db.captureDao().observeAll().first().isEmpty())
            assertTrue("notes", db.noteDao().observeAll().first().isEmpty())
            assertTrue("tasks", db.taskDao().observeAll().first().isEmpty())
            assertTrue("reminders", db.reminderDao().observeAll().first().isEmpty())

            // KEPT today — P5 (B5) must turn each of these into REMOVED.
            assertEquals("learned_rules kept", 1, db.learnedRuleDao().observeAll().first().size)
            val history = db.aiSuggestionHistoryDao().observeAll().first()
            assertEquals("ai_suggestion_history kept", 1, history.size)
            // The thought it came from is gone, but its text snippet stays.
            assertNull(history.single().captureId)
            assertEquals("Buy milk tomorrow", history.single().sourceTextSnippet)
            assertEquals("person_memory kept", 1, db.personMemoryDao().observeAll().first().size)
            assertEquals("project_memory kept", 1, db.projectMemoryDao().observeAll().first().size)
            val settingsAfter = container.appSettingsRepository.settings.first()
            assertEquals("settings kept", settingsBefore, settingsAfter)
            assertEquals(true, settingsAfter.sortRightAfterSaving)
            assertEquals(SettingsThemeMode.Dark, settingsAfter.themeMode)
        }

        // Reset cancels the reminder's backup work before deleting the row.
        assertTrue(
            "the reminder's unique work is no longer enqueued",
            reminderWork(reminderId).none { it.state == WorkInfo.State.ENQUEUED },
        )
        assertTrue(reminderWork(reminderId).all { it.state == WorkInfo.State.CANCELLED })
    }

    /** Seeds one row of each kind and a non-default setting; returns the reminder id. */
    private suspend fun seed(now: Long): Long {
        val spaceId = container.spaceRepository.insert(
            SpaceEntity(name = "Garden", icon = "home", colorAccent = "#000000", sortOrder = 0),
        )
        val captureId = container.captureRepository.insert(CaptureEntity(rawText = "Buy milk tomorrow"))
        container.noteRepository.insert(NoteEntity(title = "Seed list", body = "", spaceId = spaceId))
        container.taskRepository.insert(TaskEntity(title = "Buy milk", spaceId = spaceId))
        val reminderId = container.reminderRepository.insert(
            ReminderEntity(title = "Water the plants", dueAt = now + 86_400_000, spaceId = spaceId),
        )
        val historyId = container.aiSuggestionHistoryRepository.insert(
            AiSuggestionHistoryEntity(
                surface = AiSuggestionSurface.Capture,
                outcome = AiSuggestionOutcome.Accepted,
                analyzerSource = "Local",
                captureId = captureId,
                sourceTextSnippet = "Buy milk tomorrow",
            ),
        )
        container.learnedRuleRepository.insert(
            LearnedRuleEntity(title = "Groceries are tasks", ruleText = "Shopping thoughts become tasks", sourceSuggestionHistoryId = historyId),
        )
        container.personMemoryRepository.insert(PersonMemoryEntity(displayName = "Test contact"))
        container.projectMemoryRepository.insert(ProjectMemoryEntity(name = "Garden plan"))
        container.appSettingsRepository.edit { it.copy(sortRightAfterSaving = true, themeMode = SettingsThemeMode.Dark) }
        return reminderId
    }

    private fun reminderWork(reminderId: Long): List<WorkInfo> =
        WorkManager.getInstance(application).getWorkInfosForUniqueWork("orbit_reminder_$reminderId").get()
}
