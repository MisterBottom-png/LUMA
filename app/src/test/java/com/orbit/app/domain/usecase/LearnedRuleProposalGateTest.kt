package com.orbit.app.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.repository.RoomAiCorrectionHistoryRepository
import com.orbit.app.data.repository.RoomLearnedRuleRepository
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.learnedRulesReachGemini
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** "Save a learning preference?" is offered only when the rule would be used. */
@RunWith(AndroidJUnit4::class)
class LearnedRuleProposalGateTest {
    private lateinit var database: OrbitDatabase

    @Before
    fun setUp() = runBlocking {
        database = inMemoryOrbitDatabase()
        repeat(2) {
            database.aiCorrectionHistoryDao().insert(
                AiCorrectionHistoryEntity(fieldName = "type", originalValue = "Note", correctedValue = "Task"),
            )
        }
    }

    @After
    fun tearDown() = database.close()

    private fun useCase(settings: AppSettings) = ProposeLearnedRuleUseCase(
        correctionHistoryRepository = RoomAiCorrectionHistoryRepository(database.aiCorrectionHistoryDao()),
        learnedRuleRepository = RoomLearnedRuleRepository(database.learnedRuleDao()),
        isLearningEnabled = { settings.enableLocalAiLearning },
        rulesAreUsed = { settings.learnedRulesReachGemini },
    )

    @Test
    fun noOfferWhenTheRuleWouldNotReachGemini() = runBlocking {
        val learningOnly = AppSettings(enableLocalAiLearning = true, shareLocalLearningWithGemini = false, aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion)
        val sharingButLocal = AppSettings(enableLocalAiLearning = true, shareLocalLearningWithGemini = true, aiMode = AiMode.LocalOnly)
        assertFalse(learningOnly.learnedRulesReachGemini)
        assertFalse(sharingButLocal.learnedRulesReachGemini)
        assertNull(useCase(learningOnly).proposalAfterCorrection())
        assertNull(useCase(sharingButLocal).proposalAfterCorrection())
    }

    @Test
    fun offeredWhenLearningSharingAndGeminiAreOn() = runBlocking {
        val all = AppSettings(enableLocalAiLearning = true, shareLocalLearningWithGemini = true, aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion)
        assertTrue(all.learnedRulesReachGemini)
        assertNotNull(useCase(all).proposalAfterCorrection())
    }

    @Test
    fun theNextPatternIsOfferedAfterNotNow_andTheQuestionNamesTheChoice() = runBlocking {
        // N016 + B12: "Not now" is remembered and the next repeated correction is offered.
        repeat(2) {
            database.aiCorrectionHistoryDao().insert(
                AiCorrectionHistoryEntity(fieldName = "space", originalValue = "Home", correctedValue = "Work"),
            )
        }
        val declined = mutableSetOf<String>()
        val memory = object : com.orbit.app.data.local.DeclinedRuleMemory {
            override fun isDeclined(key: String) = key in declined
            override fun decline(key: String) { declined += key }
            override fun clear() = declined.clear()
        }
        val all = AppSettings(enableLocalAiLearning = true, shareLocalLearningWithGemini = true, aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion)
        val useCase = ProposeLearnedRuleUseCase(
            correctionHistoryRepository = RoomAiCorrectionHistoryRepository(database.aiCorrectionHistoryDao()),
            learnedRuleRepository = RoomLearnedRuleRepository(database.learnedRuleDao()),
            isLearningEnabled = { all.enableLocalAiLearning },
            rulesAreUsed = { all.learnedRulesReachGemini },
            declined = memory,
        )

        val first = requireNotNull(useCase.proposalAfterCorrection())
        useCase.decline(first)
        val second = requireNotNull(useCase.proposalAfterCorrection())

        assertTrue(first.key != second.key)
        assertTrue(setOf(first.from to first.to, second.from to second.to) == setOf("Note" to "Task", "Home" to "Work"))
        useCase.decline(second)
        assertNull(useCase.proposalAfterCorrection())
    }
}
