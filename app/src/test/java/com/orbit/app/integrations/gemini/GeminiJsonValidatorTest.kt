package com.orbit.app.integrations.gemini

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiJsonValidatorTest {
    @Test
    fun connectionOkAcceptsTrueJson() {
        assertTrue(GeminiJsonValidator.isConnectionOk("""{"ok": true}"""))
    }

    @Test
    fun connectionOkRejectsFalseOrInvalidJson() {
        assertFalse(GeminiJsonValidator.isConnectionOk("""{"ok": false}"""))
        assertFalse(GeminiJsonValidator.isConnectionOk("not json"))
    }

    @Test
    fun captureAnalysisAcceptsValidSchema() {
        val result = GeminiJsonValidator.captureAnalysis(
            text = """
                {
                  "suggestedType": "task",
                  "suggestedSpaceName": "Work",
                  "suggestedTitle": "Send manager the update",
                  "summary": "A work follow-up for manager.",
                  "possibleMondayItem": true,
                  "suggestedNextAction": "Send manager the update",
                  "relatedTopics": ["manager", "Monday"],
                  "suggestionChips": ["Work", "Tomorrow"],
                  "reminderPossible": false,
                  "reminderSuggestion": {"dueAtEpochMillis": 1783526400000, "phrase": "tomorrow"},
                  "lifeSignal": "waiting_for",
                  "confidence": 0.82,
                  "typeReason": "Action wording",
                  "spaceReason": "Mentions manager"
                }
            """.trimIndent(),
            fallbackRawText = "send manager the update",
        )

        assertNotNull(result)
        checkNotNull(result)
        assertEquals("send manager the update", result.rawText)
        assertEquals(SuggestedItemType.Task, result.suggestedType)
        assertEquals("Work", result.suggestedSpaceName)
        assertEquals("Send manager the update", result.suggestedTitle)
        assertEquals(listOf("manager", "Monday"), result.relatedTopics)
        assertEquals(CaptureLifeSignal.WaitingFor, result.lifeSignal)
        assertEquals(CaptureAnalyzerSource.Gemini, result.analyzerSource)
        assertEquals(1783526400000L, result.suggestedReminderAt)
        assertEquals("tomorrow", result.reminderPhrase)
        assertEquals(0.82f, result.confidence, 0.001f)
    }

    @Test
    fun captureAnalysisRejectsInvalidTypeOrConfidence() {
        assertNull(
            GeminiJsonValidator.captureAnalysis(
                text = """{"suggestedType":"event","suggestedSpaceName":"Work","suggestedNextAction":"Do it","confidence":0.8}""",
                fallbackRawText = "Do it",
            ),
        )
        assertNull(
            GeminiJsonValidator.captureAnalysis(
                text = """{"suggestedType":"task","suggestedSpaceName":"Work","suggestedNextAction":"Do it","confidence":1.7}""",
                fallbackRawText = "Do it",
            ),
        )
    }

    @Test
    fun captureAnalysisFallsBackToInboxForUnknownSpace() {
        val result = GeminiJsonValidator.captureAnalysis(
            text = """{"suggestedType":"note","suggestedSpaceName":"Unknown","suggestedNextAction":"Keep this reflection","confidence":0.7}""",
            fallbackRawText = "olen mures raha pärast",
            allowedSpaces = listOf("Inbox", "Money"),
        )

        assertNotNull(result)
        assertEquals("Inbox", checkNotNull(result).suggestedSpaceName)
    }

    @Test
    fun brainDumpSuggestionsAcceptValidItemsAndDropBadOnes() {
        val result = GeminiJsonValidator.brainDumpSuggestions(
            text = """
                {
                  "items": [
                    {
                      "rawText": "dog food",
                      "title": "Buy dog food",
                      "suggestedType": "task",
                      "suggestedSpaceName": "Dog",
                      "tinyNextAction": "Check the food bag",
                      "confidence": "high",
                      "reason": "Dog food is actionable."
                    },
                    {
                      "rawText": "bad",
                      "title": "",
                      "suggestedType": "event",
                      "confidence": "high"
                    }
                  ]
                }
            """.trimIndent(),
            allowedSpaces = listOf("Inbox", "Dog"),
        )

        assertNotNull(result)
        checkNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Buy dog food", result.first().title)
        assertEquals(SuggestedItemType.Task, result.first().suggestedType)
        assertEquals("Dog", result.first().suggestedSpaceName)
    }

    @Test
    fun tinyActionAcceptsCommonGeminiFieldVariants() {
        assertEquals("Write one sentence.", GeminiJsonValidator.tinyAction("""{"tinyAction":"Write one sentence."}"""))
        assertEquals("Open the document.", GeminiJsonValidator.tinyAction("""{"tinyStep":"Open the document."}"""))
        assertEquals("List three questions.", GeminiJsonValidator.tinyAction("""{"action":"List three questions."}"""))
        assertEquals("Check the first bill.", GeminiJsonValidator.tinyAction("""{"nextAction":"Check the first bill."}"""))
    }
}
