package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.SuggestedItemType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRulesCaptureAnalyzerTest {
    private val analyzer: CaptureAnalyzer = LocalRulesCaptureAnalyzer()

    @Test
    fun taskSignalsSuggestTaskCaseInsensitively() {
        val signals = listOf("ask", "CALL", "send", "need to", "must", "remind")

        signals.forEach { signal ->
            val result = analyzer.analyze("$signal manager about the report")

            assertEquals("Signal: $signal", SuggestedItemType.Task, result.suggestedType)
        }
    }

    @Test
    fun spaceSignalsSuggestTheirConfiguredSpace() {
        val expectedSpaces = mapOf(
            "manager" to "Work",
            "stakeholder" to "Work",
            "data governance" to "Work",
            "change management" to "Work",
            "Monday" to "Work",
            "car" to "Car",
            "Audi" to "Car",
            "Lexus" to "Car",
            "Mazda" to "Car",
            "dog" to "Dog",
            "money" to "Money",
            "pay" to "Money",
            "salary" to "Money",
            "budget" to "Money",
            "idea" to "Ideas",
            "maybe" to "Ideas",
            "app" to "Ideas",
            "concept" to "Ideas",
        )

        expectedSpaces.forEach { (signal, expectedSpace) ->
            val result = analyzer.analyze("Notes about $signal.")

            assertEquals("Signal: $signal", expectedSpace, result.suggestedSpaceName)
            assertTrue("Signal: $signal", result.relatedTopics.isNotEmpty())
        }
    }

    @Test
    fun dateSignalsMarkReminderAsPossible() {
        listOf("today", "tomorrow", "next week").forEach { signal ->
            val result = analyzer.analyze("Think about this $signal")

            assertTrue("Signal: $signal", result.reminderPossible)
        }
    }

    @Test
    fun compactReminderTimeBecomesStructuredInterpretationData() {
        val zone = ZoneId.of("Europe/Tallinn")
        val fixedAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { zone },
        )

        val result = fixedAnalyzer.analyze(
            "Reminder test. A test of the reminder functionality set for 1600 today.",
        )
        val localTime = Instant.ofEpochMilli(requireNotNull(result.suggestedReminderAt))
            .atZone(zone)
            .toLocalTime()

        assertEquals(ReminderTimeStatus.Resolved, result.reminderTimeStatus)
        assertEquals(LocalTime.of(16, 0), localTime)
        assertEquals("16:00 today", result.reminderPhrase)
    }

    @Test
    fun workTaskCanBeSuggestedAsMondayItem() {
        val result = analyzer.analyze("Call manager")

        assertTrue(result.possibleMondayItem)
        assertEquals("Work", result.suggestedSpaceName)
        assertEquals(SuggestedItemType.Task, result.suggestedType)
    }

    @Test
    fun mondaySignalMarksPossibleMondayItem() {
        val result = analyzer.analyze("Monday planning notes")

        assertTrue(result.possibleMondayItem)
    }

    @Test
    fun rawTextIsPreservedExactly() {
        val rawText = "  Call manager tomorrow.  \n"

        val result = analyzer.analyze(rawText)

        assertEquals(rawText, result.rawText)
        assertEquals(SuggestedItemType.Task, result.suggestedType)
        assertEquals("Work", result.suggestedSpaceName)
        assertTrue(result.reminderPossible)
        assertTrue(result.suggestedNextAction.isNotBlank())
        assertTrue(result.confidence in 0.0f..1.0f)
    }

    @Test
    fun unrelatedTextDefaultsToPersonalNote() {
        val result = analyzer.analyze("A quiet observation")

        assertEquals(SuggestedItemType.Note, result.suggestedType)
        assertEquals("Personal", result.suggestedSpaceName)
        assertEquals(listOf("Personal"), result.relatedTopics)
        assertEquals(CaptureConfidence.Low, result.confidenceLevel)
        assertTrue(result.typeReason.isNotBlank())
        assertTrue(result.spaceReason.isNotBlank())
        assertFalse(result.possibleMondayItem)
        assertFalse(result.reminderPossible)
    }

    @Test
    fun strongWorkTaskHasHighConfidenceAndReasons() {
        val result = analyzer.analyze("Ask manager about data governance tomorrow")

        assertEquals(CaptureConfidence.High, result.confidenceLevel)
        assertTrue(result.typeReason.contains("Time") || result.typeReason.contains("Action"))
        assertTrue(result.spaceReason.contains("Work"))
    }

    @Test
    fun signalsOnlyMatchCompleteWords() {
        val result = analyzer.analyze("A callback about masking tape")

        assertEquals(SuggestedItemType.Note, result.suggestedType)
    }

    @Test
    fun blankCaptureIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyze("  \n")
        }
    }

    @Test
    fun multilineBrainDumpReturnsReviewableSuggestionsAndPreservesRawText() {
        val rawText = """
            Need dog food
            Ask manager about change project
            Car sound again
            Maybe start saving money
            Learning Kotlin notes
        """.trimIndent()

        val result = analyzer.analyze(rawText)

        assertEquals(rawText, result.rawText)
        assertEquals("Inbox", result.suggestedSpaceName)
        assertEquals(5, result.brainDumpItems.size)
        assertEquals("Dog", result.brainDumpItems[0].suggestedSpaceName)
        assertEquals(SuggestedItemType.Task, result.brainDumpItems[1].suggestedType)
        assertEquals("Car", result.brainDumpItems[2].suggestedSpaceName)
        assertEquals("Money", result.brainDumpItems[3].suggestedSpaceName)
        assertEquals("Learning", result.brainDumpItems[4].suggestedSpaceName)
        assertTrue(result.brainDumpItems.all { it.tinyNextAction.isNotBlank() })
    }
}
