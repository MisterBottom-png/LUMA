package com.orbit.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.security.GeminiApiKeyStore
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** F10, B7, N036: Reset is a fresh start; nothing from before survives. */
@RunWith(AndroidJUnit4::class)
class LocalDataResetTest {
    private val database = inMemoryOrbitDatabase()

    @After
    fun tearDown() = database.close()

    @Test
    fun resetLeavesNothingBehind() = runBlocking {
        database.spaceDao().insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0))
        val reminderId = database.reminderDao().insert(ReminderEntity(title = "Water the roses", dueAt = 5_000_000L))
        database.captureDao().insert(CaptureEntity(rawText = "Water the roses"))
        database.aiSuggestionHistoryDao().insert(
            AiSuggestionHistoryEntity(
                surface = AiSuggestionSurface.Capture,
                outcome = AiSuggestionOutcome.Accepted,
                analyzerSource = "local",
            ),
        )
        database.learnedRuleDao().insert(LearnedRuleEntity(title = "Bills", ruleText = "Bills go to Home"))
        val settings = MemorySettings(AppSettings(hasCompletedFirstTimeTutorial = true, lastReceiptCaptureId = 4L))
        val key = MemoryKey("a-key")
        val cancelled = mutableListOf<Long>()
        var preferencesCleared = false
        var notificationsCancelled = false

        LocalDataReset(
            database = database,
            scheduler = object : ReminderScheduler {
                override fun schedule(reminder: ReminderEntity): String? = null
                override fun cancel(reminderId: Long) { cancelled += reminderId }
            },
            settings = settings,
            geminiKey = key,
            clearPreferences = { preferencesCleared = true },
            cancelNotifications = { notificationsCancelled = true },
        ).run()

        assertEquals(listOf(reminderId), cancelled)
        assertTrue(database.spaceDao().observeAll().first().isEmpty())
        assertTrue(database.reminderDao().getAll().isEmpty())
        assertTrue(database.captureDao().observeAll().first().isEmpty())
        assertTrue(database.aiSuggestionHistoryDao().observeAll().first().isEmpty())
        assertTrue(database.learnedRuleDao().observeAll().first().isEmpty())
        assertFalse(settings.state.value.hasCompletedFirstTimeTutorial)
        assertNull(settings.state.value.lastReceiptCaptureId)
        assertNull(key.value)
        assertTrue(preferencesCleared)
        assertTrue(notificationsCancelled)
    }

    private class MemorySettings(initial: AppSettings) : AppSettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings = state
        override suspend fun update(settings: AppSettings) { state.value = settings }
        override suspend fun reset() { state.value = AppSettings() }
    }

    private class MemoryKey(var value: String?) : GeminiApiKeyStore {
        override suspend fun saveKey(apiKey: String) { value = apiKey }
        override suspend fun getKey(): String? = value
        override suspend fun hasKey(): Boolean = value != null
        override suspend fun deleteKey() { value = null }
    }
}
