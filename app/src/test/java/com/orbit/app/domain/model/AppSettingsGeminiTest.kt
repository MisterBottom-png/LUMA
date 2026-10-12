package com.orbit.app.domain.model

import com.orbit.app.domain.ai.OrbitAiRouter
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.integrations.gemini.GeminiApiClient
import com.orbit.app.integrations.gemini.GeminiApiErrorKind
import com.orbit.app.integrations.gemini.GeminiApiResult
import com.orbit.app.integrations.gemini.geminiError
import com.orbit.app.security.GeminiApiKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's first-run line says a save goes to Gemini exactly when the router would send it. */
class AppSettingsGeminiTest {
    @Test
    fun localOnly_keepsHomeSavesOnThePhone() {
        val settings = AppSettings(
            aiMode = AiMode.LocalOnly,
            useGeminiForCapture = true,
            useGeminiForBrainDump = true,
        )

        assertFalse(settings.homeSavesUseGemini(hasGeminiKey = true))
    }

    @Test
    fun geminiWithBothSwitchesOff_keepsHomeSavesOnThePhone() {
        val settings = AppSettings(aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion)

        assertFalse(settings.homeSavesUseGemini(hasGeminiKey = true))
    }

    @Test
    fun geminiWithEitherSwitchOn_sendsHomeSaves() {
        assertTrue(AppSettings(aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion, useGeminiForCapture = true).homeSavesUseGemini(hasGeminiKey = true))
        assertTrue(AppSettings(aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion, useGeminiForBrainDump = true).homeSavesUseGemini(hasGeminiKey = true))
    }

    @Test
    fun brainDumpSwitchAlone_sendsOnlyBrainDumps() {
        val brainDumpOnly = AppSettings(aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion, useGeminiForBrainDump = true)
        val both = brainDumpOnly.copy(useGeminiForCapture = true)

        assertTrue(brainDumpOnly.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey = true))
        assertFalse(both.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey = true))
        assertFalse(brainDumpOnly.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey = false))
        assertFalse(AppSettings().homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey = true))
    }

    @Test
    fun withoutASavedKey_homeSavesStayOnThePhone() {
        val settings = AppSettings(
            aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion,
            useGeminiForCapture = true,
            useGeminiForBrainDump = true,
        )

        assertFalse(settings.homeSavesUseGemini(hasGeminiKey = false))
    }

    @Test
    fun otherGeminiSwitches_doNotSendHomeSaves() {
        val settings = AppSettings(
            aiMode = AiMode.GeminiApi, geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion,
            useGeminiForMakeSmaller = true,
            useGeminiForSituation = true,
            useGeminiForReview = true,
        )

        assertFalse(settings.homeSavesUseGemini(hasGeminiKey = true))
    }

    @Test
    fun theRouterSendsAHomeSaveOnlyWhenTheLineSaysSo() = runBlocking {
        for (mode in AiMode.entries) {
            for (capture in listOf(false, true)) {
                for (brainDump in listOf(false, true)) {
                    for (hasKey in listOf(false, true)) {
                        val settings = AppSettings(
                            aiMode = mode,
                            useGeminiForCapture = capture,
                            useGeminiForBrainDump = brainDump,
                            geminiConsentVersion = com.orbit.app.domain.model.GeminiConsent.CurrentVersion,
                        )
                        val client = CountingGeminiClient()
                        val router = OrbitAiRouter(
                            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(),
                            geminiApiClient = client,
                            geminiApiKeyStore = KeyStore(hasKey),
                        )

                        router.analyzeCapture(rawText = "Buy milk tomorrow", settings = settings)
                        val case = "mode=$mode capture=$capture brainDump=$brainDump key=$hasKey"
                        // A single thought stays local both when the line says so and when
                        // only Brain Dumps go to Gemini.
                        if (!settings.homeSavesUseGemini(hasGeminiKey = hasKey) ||
                            settings.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey = hasKey)
                        ) {
                            assertEquals(case, 0, client.requests)
                        }
                        // Capture sorting sends every save, not only Brain Dumps.
                        if (mode == AiMode.GeminiApi && capture && hasKey) {
                            assertTrue(case, client.requests > 0)
                        }
                    }
                }
            }
        }
    }

    private class CountingGeminiClient : GeminiApiClient {
        var requests = 0

        override suspend fun generateJson(
            apiKey: String,
            modelId: String,
            prompt: String,
            maxOutputTokens: Int,
        ): GeminiApiResult {
            requests++
            return GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
        }

        override suspend fun testConnection(apiKey: String, modelId: String): GeminiApiResult =
            GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
    }

    private class KeyStore(private val hasKey: Boolean) : GeminiApiKeyStore {
        override suspend fun saveKey(apiKey: String) = Unit
        override suspend fun getKey(): String? = if (hasKey) "test-key" else null
        override suspend fun hasKey(): Boolean = hasKey
        override suspend fun deleteKey() = Unit
    }

    @Test
    fun withoutCurrentConsentNothingIsSent() = runBlocking {
        // N047: the consent gate holds on the call path too, not only when settings are read.
        val client = CountingGeminiClient()
        val router = OrbitAiRouter(
            localCaptureAnalyzer = LocalRulesCaptureAnalyzer(),
            geminiApiClient = client,
            geminiApiKeyStore = KeyStore(true),
        )
        router.analyzeCapture(
            rawText = "Buy milk tomorrow",
            settings = AppSettings(aiMode = AiMode.GeminiApi, useGeminiForCapture = true, geminiConsentVersion = 1),
        )
        assertEquals(0, client.requests)
    }
}
