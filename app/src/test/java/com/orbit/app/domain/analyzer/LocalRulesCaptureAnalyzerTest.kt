package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.SuggestedItemType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
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
    fun estonianRulesClassifyReminderAndInflectedLifeAreaWords() {
        val localeAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { ZoneId.of("Europe/Tallinn") },
            locale = { Locale.forLanguageTag("et") },
        )

        val reminder = localeAnalyzer.analyze("Tuleta mulle meelde homme kell 1600")
        val task = localeAnalyzer.analyze("Osta koerale toit homme kell 1800")

        assertEquals(SuggestedItemType.Reminder, reminder.suggestedType)
        assertEquals(ReminderTimeStatus.Resolved, reminder.reminderTimeStatus)
        assertEquals("16:00 homme", reminder.reminderPhrase)
        assertEquals(SuggestedItemType.Task, task.suggestedType)
        assertEquals("Dog", task.suggestedSpaceName)
    }

    @Test
    fun russianRulesClassifyReminderAndTaskWithoutEnglishSignals() {
        val localeAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { ZoneId.of("Europe/Tallinn") },
            locale = { Locale.forLanguageTag("ru") },
        )

        val reminder = localeAnalyzer.analyze("Напомни мне завтра в 16:00")
        val task = localeAnalyzer.analyze("Купить корм собаке завтра в 1800")

        assertEquals(SuggestedItemType.Reminder, reminder.suggestedType)
        assertEquals(ReminderTimeStatus.Resolved, reminder.reminderTimeStatus)
        assertEquals("16:00 завтра", reminder.reminderPhrase)
        assertEquals(SuggestedItemType.Task, task.suggestedType)
        assertEquals("Dog", task.suggestedSpaceName)
    }

    @Test
    fun mixedLanguageCaptureCombinesDetectedRulePacks() {
        val localeAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { ZoneId.of("Europe/Tallinn") },
            locale = { Locale.forLanguageTag("et") },
        )

        val result = localeAnalyzer.analyze("Saada report tomorrow at 1600")

        assertEquals(SuggestedItemType.Task, result.suggestedType)
        assertEquals(ReminderTimeStatus.Resolved, result.reminderTimeStatus)
        assertEquals("16:00 tomorrow", result.reminderPhrase)
    }

    @Test
    fun ordinaryEmbeddedNumberIsNotAFalsePositiveReminder() {
        val localeAnalyzer = LocalRulesCaptureAnalyzer(
            locale = { Locale.forLanguageTag("et") },
        )

        val result = localeAnalyzer.analyze("Eelarve 1600 eurot")

        assertFalse(result.reminderPossible)
        assertEquals(ReminderTimeStatus.Unspecified, result.reminderTimeStatus)
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

    @Test
    fun brainDumpKeepsShortDuplicateFragmentsStableAndPreservesReminderIntent() {
        val result = analyzer.analyze("x\nx\nremind me tomorrow at 1600")

        assertEquals(listOf("brain:1", "brain:2", "brain:3"), result.brainDumpItems.map { it.id })
        assertEquals(listOf("x", "x", "remind me tomorrow at 1600"), result.brainDumpItems.map { it.rawText })
        assertEquals(SuggestedItemType.Reminder, result.brainDumpItems.last().suggestedType)
        assertEquals(ReminderTimeStatus.Resolved, result.brainDumpItems.last().reminderTimeStatus)
        assertTrue(result.brainDumpItems.last().suggestedReminderAt != null)
    }

    @Test
    fun brainDumpTaskForNextMonthGetsAnEditableDueDate() {
        val zone = ZoneId.of("Europe/Tallinn")
        val fixedAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { zone },
        )

        val item = fixedAnalyzer
            .analyze("first item\ntesting brain dump task for next month")
            .brainDumpItems
            .last()
        val dueAt = Instant.ofEpochMilli(requireNotNull(item.suggestedReminderAt)).atZone(zone)

        assertEquals(SuggestedItemType.Task, item.suggestedType)
        assertEquals(LocalDate.of(2026, 8, 14), dueAt.toLocalDate())
        assertEquals(LocalTime.of(23, 59), dueAt.toLocalTime())
    }

    @Test
    fun brainDumpTaskForNextMonthAt1500KeepsBothDateAndTime() {
        val zone = ZoneId.of("Europe/Tallinn")
        val fixedAnalyzer = LocalRulesCaptureAnalyzer(
            now = { Instant.parse("2026-07-14T10:00:00Z") },
            zoneId = { zone },
        )

        val item = fixedAnalyzer
            .analyze("first item\ntesting brain dump task for next month at 1500")
            .brainDumpItems
            .last()
        val dueAt = Instant.ofEpochMilli(requireNotNull(item.suggestedReminderAt)).atZone(zone)

        assertEquals(SuggestedItemType.Task, item.suggestedType)
        assertEquals(LocalDate.of(2026, 8, 14), dueAt.toLocalDate())
        assertEquals(LocalTime.of(15, 0), dueAt.toLocalTime())
    }
}
