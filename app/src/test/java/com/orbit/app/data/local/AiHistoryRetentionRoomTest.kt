package com.orbit.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiHistoryRetentionRoomTest {
    private lateinit var database: OrbitDatabase
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    private fun history(createdAt: Long) = AiSuggestionHistoryEntity(
        surface = AiSuggestionSurface.Capture,
        outcome = AiSuggestionOutcome.Corrected,
        analyzerSource = "Local",
        sourceTextSnippet = "a private thought",
        createdAt = createdAt,
    )

    @Test
    fun oldHistoryIsRemoved_recentHistoryAndLearnedRulesStay() = runBlocking {
        val oldId = database.aiSuggestionHistoryDao().insert(history(now - 120 * day))
        database.aiSuggestionHistoryDao().insert(history(now - 10 * day))
        val oldCorrection = database.aiCorrectionHistoryDao().insert(
            AiCorrectionHistoryEntity(suggestionHistoryId = oldId, fieldName = "space", correctedValue = "Home", createdAt = now - 120 * day),
        )
        val ruleId = database.learnedRuleDao().insert(
            LearnedRuleEntity(
                title = "Space preference",
                ruleText = "Garden goes to Home",
                sourceSuggestionHistoryId = oldId,
                sourceCorrectionHistoryId = oldCorrection,
                createdAt = now - 120 * day,
            ),
        )

        val removed = AiHistoryRetention.prune(database, now)

        assertEquals(2, removed)
        assertEquals(1, database.aiSuggestionHistoryDao().observeAll().first().size)
        assertEquals(0, database.aiCorrectionHistoryDao().observeAll().first().size)
        val rule = database.learnedRuleDao().getById(ruleId)!!
        assertNull(rule.sourceSuggestionHistoryId)
        assertNull(rule.sourceCorrectionHistoryId)
    }
}
