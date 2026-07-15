package com.orbit.app.integrations.gemini

import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptBuildersTest {
    @Test
    fun capturePromptRequiresJsonOnlyAndNoRecordCreation() {
        val prompt = GeminiPromptBuilders.captureAnalysis("ask manager about Monday board")

        assertTrue(prompt.contains("Return JSON only"))
        assertTrue(prompt.contains("Never create tasks"))
        assertTrue(prompt.contains("suggestedType"))
        assertFalse(prompt.contains("Orbit"))
    }

    @Test
    fun tinyActionPromptUsesLumaAndJsonSchema() {
        val prompt = GeminiPromptBuilders.tinyAction("fix money situation")

        assertTrue(prompt.contains("LUMA"))
        assertTrue(prompt.contains("tinyAction"))
        assertTrue(prompt.contains("Return JSON only"))
    }

    @Test
    fun capturePromptIncludesCompactLearningProfileWhenProvided() {
        val prompt = GeminiPromptBuilders.captureAnalysis(
            rawText = "ask manager about Monday board",
            learningProfile = "- manager usually maps to Work",
        )

        assertTrue(prompt.contains("Relevant local learning profile"))
        assertTrue(prompt.contains("manager usually maps to Work"))
        assertTrue(prompt.contains("not as a factual source"))
    }

    @Test
    fun capturePromptSuppliesDeviceTimezoneAndRequiresConsistentTemporalFields() {
        val prompt = GeminiPromptBuilders.captureAnalysis(
            rawText = "remind me at 1600 today",
            nowEpochMillis = 1_784_025_000_000L,
            zoneId = ZoneId.of("Europe/Tallinn"),
        )

        assertTrue(prompt.contains("Europe/Tallinn"))
        assertTrue(prompt.contains("phrase and epoch must describe the same instant"))
    }
}
