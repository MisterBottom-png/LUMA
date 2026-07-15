package com.orbit.app.domain.ai

import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.integrations.gemini.GeminiApiClient
import com.orbit.app.integrations.gemini.GeminiApiErrorKind
import com.orbit.app.integrations.gemini.GeminiApiResult
import com.orbit.app.integrations.gemini.geminiError
import com.orbit.app.security.GeminiApiKeyStore
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitAiRouterTest {
    @Test
    fun explicitLocalTimeReplacesConflictingGeminiEpochAndPhrase() = runBlocking {
        val client = FakeGeminiClient(
            responses = mapOf(
                FastModel to GeminiApiResult.Success(
                    """
                    {
                      "suggestedTitle": "Reminder test",
                      "summary": "Reminder test",
                      "suggestedType": "reminder",
                      "suggestedSpaceName": "Inbox",
                      "possibleMondayItem": false,
                      "suggestedNextAction": "Set the reminder",
                      "relatedTopics": ["Reminder"],
                      "suggestionChips": ["Reminder"],
                      "reminderPossible": true,
                      "reminderSuggestion": {"dueAtEpochMillis": 1784054400000, "phrase": "16:00 today"},
                      "lifeSignal": "none",
                      "confidence": 0.91,
                      "typeReason": "Explicit time",
                      "spaceReason": "No specific Space"
                    }
                    """.trimIndent(),
                    FastModel,
                ),
            ),
        )
        val zone = ZoneId.of("Europe/Tallinn")
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(
                now = { Instant.parse("2026-07-14T10:00:00Z") },
                zoneId = { zone },
            ),
            geminiApiClient = client,
            geminiApiKeyStore = FakeKeyStore,
        )

        val result = router.analyzeCapture(
            rawText = "Reminder test set for 1600 today",
            settings = AppSettings(
                aiMode = AiMode.GeminiApi,
                geminiFastModelId = FastModel,
                useGeminiForCapture = true,
            ),
            allowedSpaces = listOf("Inbox"),
        )
        val local = Instant.ofEpochMilli(requireNotNull(result.analysis.suggestedReminderAt)).atZone(zone)

        assertEquals(LocalTime.of(16, 0), local.toLocalTime())
        assertEquals("16:00 today", result.analysis.reminderPhrase)
    }

    @Test
    fun invalidCompactTimeClearsGeminiTimestampForClarification() = runBlocking {
        val client = FakeGeminiClient(
            responses = mapOf(
                FastModel to GeminiApiResult.Success(
                    """
                    {
                      "suggestedType": "reminder",
                      "suggestedSpaceName": "Inbox",
                      "suggestedNextAction": "Set the reminder",
                      "reminderPossible": true,
                      "reminderSuggestion": {"dueAtEpochMillis": 1784054400000, "phrase": "later today"},
                      "confidence": 0.9
                    }
                    """.trimIndent(),
                    FastModel,
                ),
            ),
        )
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(
                now = { Instant.parse("2026-07-14T10:00:00Z") },
                zoneId = { ZoneId.of("Europe/Tallinn") },
            ),
            geminiApiClient = client,
            geminiApiKeyStore = FakeKeyStore,
        )

        val result = router.analyzeCapture(
            rawText = "Reminder set for 2460 today",
            settings = AppSettings(
                aiMode = AiMode.GeminiApi,
                geminiFastModelId = FastModel,
                useGeminiForCapture = true,
            ),
            allowedSpaces = listOf("Inbox"),
        )

        assertEquals(null, result.analysis.suggestedReminderAt)
        assertEquals(null, result.analysis.reminderPhrase)
    }

    @Test
    fun analyzeCapturePassesLearningProfileToGeminiPrompt() = runBlocking {
        val client = FakeGeminiClient(
            responses = mapOf(
                FastModel to GeminiApiResult.Success(
                    """
                    {
                      "suggestedTitle": "Ask manager about budget",
                      "summary": "Ask manager about budget",
                      "suggestedType": "task",
                      "suggestedSpaceName": "Work",
                      "possibleMondayItem": false,
                      "suggestedNextAction": "Ask manager about budget",
                      "relatedTopics": ["manager"],
                      "suggestionChips": ["Task", "Work"],
                      "reminderPossible": false,
                      "reminderSuggestion": {"dueAtEpochMillis": null, "phrase": null},
                      "lifeSignal": "none",
                      "confidence": 0.82,
                      "typeReason": "This sounds actionable.",
                      "spaceReason": "manager maps to Work."
                    }
                    """.trimIndent(),
                    FastModel,
                ),
            ),
        )
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(),
            geminiApiClient = client,
            geminiApiKeyStore = FakeKeyStore,
            learningProfileProvider = StaticLearningProfileProvider("- manager usually maps to Work"),
        )

        router.analyzeCapture(
            rawText = "Ask manager about budget",
            settings = AppSettings(
                aiMode = AiMode.GeminiApi,
                geminiFastModelId = FastModel,
                useGeminiForCapture = true,
            ),
            allowedSpaces = listOf("Inbox", "Work"),
        )

        assertEquals(true, client.requestedPrompts.single().contains("manager usually maps to Work"))
    }

    @Test
    fun makeSmallerRetriesFastModelWhenReasoningModelFails() = runBlocking {
        val client = FakeGeminiClient(
            responses = mapOf(
                ReasoningModel to GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.Unknown)),
                FastModel to GeminiApiResult.Success("""{"tinyAction":"Check one small bill."}""", FastModel),
            ),
        )
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(),
            geminiApiClient = client,
            geminiApiKeyStore = FakeKeyStore,
        )

        val result = router.makeSmaller(
            text = "fix money situation",
            settings = AppSettings(
                aiMode = AiMode.GeminiApi,
                geminiFastModelId = FastModel,
                geminiReasoningModelId = ReasoningModel,
                useGeminiForMakeSmaller = true,
            ),
        )

        assertEquals(AiRouteSource.Gemini, result.metadata.source)
        assertEquals(FastModel, result.metadata.modelId)
        assertEquals("Check one small bill.", result.action)
        assertEquals(listOf(ReasoningModel, FastModel), client.requestedModels)
    }

    @Test
    fun analyzeCaptureUsesLocalFallbackWhenGeminiFails() = runBlocking {
        val client = FakeGeminiClient(
            responses = mapOf(
                FastModel to GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.Unknown)),
            ),
        )
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(),
            geminiApiClient = client,
            geminiApiKeyStore = FakeKeyStore,
        )

        val result = router.analyzeCapture(
            rawText = "Review the draft tomorrow",
            settings = AppSettings(
                aiMode = AiMode.GeminiApi,
                geminiFastModelId = FastModel,
                useGeminiForCapture = true,
            ),
            allowedSpaces = listOf("Inbox", "Work"),
        )

        assertEquals(AiRouteSource.GeminiFailedLocalUsed, result.metadata.source)
        assertFalse(result.metadata.cloudUsed)
        assertTrue(result.analysis.analyzerFailed)
        assertEquals("Review the draft tomorrow", result.analysis.rawText)
    }

    private class FakeGeminiClient(
        private val responses: Map<String, GeminiApiResult>,
    ) : GeminiApiClient {
        val requestedModels = mutableListOf<String>()
        val requestedPrompts = mutableListOf<String>()

        override suspend fun generateJson(
            apiKey: String,
            modelId: String,
            prompt: String,
            maxOutputTokens: Int,
        ): GeminiApiResult {
            requestedModels += modelId
            requestedPrompts += prompt
            return responses.getValue(modelId)
        }

        override suspend fun testConnection(apiKey: String, modelId: String): GeminiApiResult =
            responses.getValue(modelId)
    }

    private object FakeKeyStore : GeminiApiKeyStore {
        override suspend fun saveKey(apiKey: String) = Unit
        override suspend fun getKey(): String = "test-key"
        override suspend fun hasKey(): Boolean = true
        override suspend fun deleteKey() = Unit
    }

    private class StaticLearningProfileProvider(
        private val profile: String,
    ) : LearningProfileProvider {
        override suspend fun profileFor(input: String): String = profile
    }

    private companion object {
        const val FastModel = "gemini-fast"
        const val ReasoningModel = "gemini-reasoning"
    }
}
