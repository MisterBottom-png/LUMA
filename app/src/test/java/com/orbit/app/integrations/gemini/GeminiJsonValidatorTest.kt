package com.orbit.app.integrations.gemini

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GeminiJsonValidatorTest {
    @Test
    fun connectionJsonAcceptsAnyJsonObject() {
        assertTrue(GeminiJsonValidator.isConnectionJson("""{"ok": true}"""))
        assertTrue(GeminiJsonValidator.isConnectionJson("""{"status": "ready"}"""))
    }

    @Test
    fun connectionJsonRejectsNonObjectJsonOrInvalidJson() {
        assertFalse(GeminiJsonValidator.isConnectionJson("""[true]"""))
        assertFalse(GeminiJsonValidator.isConnectionJson("not json"))
    }

    @Test
    fun responseTextSkipsThoughtPartsAndUsesFinalText() {
        val candidate = JSONObject(
            """{"content":{"parts":[
                {"thought":true,"text":"internal reasoning"},
                {"text":"{\"ok\":true}"}
            ]}}""",
        )

        assertEquals("{\"ok\":true}", extractGeminiResponseText(candidate))
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
                  "suggestedNextAction": "Send manager the update",
                  "relatedTopics": ["manager"],
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
            now = 1_783_400_000_000L,
        )

        assertNotNull(result)
        checkNotNull(result)
        assertEquals("send manager the update", result.rawText)
        assertEquals(SuggestedItemType.Task, result.suggestedType)
        assertEquals("Work", result.suggestedSpaceName)
        assertEquals("Send manager the update", result.suggestedTitle)
        assertEquals(listOf("manager"), result.relatedTopics)
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

    @Test
    fun brainDumpRequiresEveryExpectedSourceExactlyOnceAndRestoresSourceOrder() {
        val expected = LocalRulesCaptureAnalyzer().analyze("first item\nremind me tomorrow at 1600").brainDumpItems
        val reordered = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[
                {"sourceId":"brain:2","title":"Second","suggestedType":"reminder","suggestedSpaceName":"Inbox","confidence":"high"},
                {"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"}
            ]}""",
            allowedSpaces = listOf("Inbox"),
            expectedItems = expected,
        )
        assertEquals(listOf("brain:1", "brain:2"), checkNotNull(reordered).map { it.id })
        assertEquals(expected.map { it.rawText }, reordered.map { it.rawText })
        assertEquals(expected.last().suggestedReminderAt, reordered.last().suggestedReminderAt)

        val incomplete = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[{"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"}]}""",
            allowedSpaces = listOf("Inbox"),
            expectedItems = expected,
        )
        assertEquals(null, incomplete)

        val duplicate = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[
                {"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"},
                {"sourceId":"brain:1","title":"Again","suggestedType":"task","suggestedSpaceName":"Inbox","confidence":"low"}
            ]}""",
            allowedSpaces = listOf("Inbox"),
            expectedItems = expected,
        )
        assertEquals(null, duplicate)

        val unknown = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[
                {"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"},
                {"sourceId":"brain:3","title":"Unknown","suggestedType":"task","suggestedSpaceName":"Inbox","confidence":"low"}
            ]}""",
            allowedSpaces = listOf("Inbox"),
            expectedItems = expected,
        )
        assertEquals(null, unknown)
    }

    @Test
    fun brainDumpPreservesResolvedLocalReminderTypeWhenGeminiSuggestsTask() {
        val expected = LocalRulesCaptureAnalyzer()
            .analyze("first item\nI should walk my dog tomorrow at 1500")
            .brainDumpItems
        val expectedReminder = expected.last()

        val result = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[
                {"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"},
                {"sourceId":"brain:2","title":"Walk my dog","suggestedType":"task","suggestedSpaceName":"Dog","confidence":"high"}
            ]}""",
            allowedSpaces = listOf("Inbox", "Dog"),
            expectedItems = expected,
        )

        val reminder = checkNotNull(result).last()
        assertEquals(SuggestedItemType.Reminder, reminder.suggestedType)
        assertEquals(expectedReminder.suggestedReminderAt, reminder.suggestedReminderAt)
    }

    @Test
    fun brainDumpPreservesScheduledLocalTaskWhenGeminiSuggestsNote() {
        val expected = LocalRulesCaptureAnalyzer()
            .analyze("first item\ntesting brain dump task for next month")
            .brainDumpItems
        val expectedTask = expected.last()

        val result = GeminiJsonValidator.brainDumpSuggestions(
            text = """{"items":[
                {"sourceId":"brain:1","title":"First","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"},
                {"sourceId":"brain:2","title":"Test Brain Dump","suggestedType":"note","suggestedSpaceName":"Inbox","confidence":"medium"}
            ]}""",
            allowedSpaces = listOf("Inbox"),
            expectedItems = expected,
        )

        val task = checkNotNull(result).last()
        assertEquals(SuggestedItemType.Task, task.suggestedType)
        assertEquals(expectedTask.suggestedReminderAt, task.suggestedReminderAt)
    }

    @Test
    fun reminderTimesInThePastOrFarFutureAreRejected() {
        val now = 1_800_000_000_000L
        fun analysisWith(due: Long) = GeminiJsonValidator.captureAnalysis(
            text = """{"suggestedType":"reminder","suggestedSpaceName":"Inbox","suggestedNextAction":"Call",
                "confidence":0.8,"reminderSuggestion":{"dueAtEpochMillis":$due,"phrase":"then"}}""",
            fallbackRawText = "call",
            now = now,
        )
        assertNull(analysisWith(now - 3_600_000L)?.suggestedReminderAt)
        assertNull(analysisWith(1_000L)?.suggestedReminderAt)
        assertNull(analysisWith(4_070_908_800_000L)?.suggestedReminderAt)
        assertNull(analysisWith(now - 3_600_000L)?.reminderPhrase)
        assertEquals(now + 3_600_000L, analysisWith(now + 3_600_000L)?.suggestedReminderAt)
    }

    @Test
    fun fencedJsonIsReadAndTruncatedJsonIsRejected() {
        val fenced = "```json\n{\"suggestedType\":\"note\",\"suggestedSpaceName\":\"Inbox\"," +
            "\"suggestedNextAction\":\"Keep\",\"confidence\":0.7}\n```"
        assertEquals(SuggestedItemType.Note, GeminiJsonValidator.captureAnalysis(fenced, "x")?.suggestedType)
        val truncated = "{\"suggestedType\":\"note\",\"suggestedSpaceName\":\"Inbox\",\"suggestedNextAc"
        assertNull(GeminiJsonValidator.captureAnalysis(truncated, "x"))
        assertNull(GeminiJsonValidator.brainDumpSuggestions("{\"items\":[{\"title\":\"a\"", emptyList()))
    }

    @Test
    fun escapedQuotesAndUnicodeSurviveRealJsonParsing() {
        val result = GeminiJsonValidator.captureAnalysis(
            text = """{"suggestedType":"note","suggestedSpaceName":"Inbox","suggestedNextAction":"Hoia \"see\" alles",
                "suggestedTitle":"Купить «молоко»","confidence":0.6}""",
            fallbackRawText = "x",
        )
        assertEquals("Hoia \"see\" alles", result?.suggestedNextAction)
        assertEquals("Купить «молоко»", result?.suggestedTitle)
    }
}
