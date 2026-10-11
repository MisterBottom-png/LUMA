package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureOutcomeReason.TimePassed
import com.orbit.app.data.local.entity.CaptureOutcomeReason.TimeUnclear
import com.orbit.app.data.local.entity.CaptureOutcomeReason.TypeUnclear
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.analyzer.confidenceLevel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The act / ask / keep rule, table-driven. The first table runs real thoughts in
 * English, Estonian and Russian through the local analyzer and then [decide], so it
 * shows what a user actually gets; the second pins each rule on a hand-built analysis.
 */
class CaptureDecisionTest {
    private val now = Instant.parse("2026-10-14T10:00:00Z") // a Wednesday, 13:00 in Tallinn

    private data class Row(
        val language: String,
        val text: String,
        val expected: CaptureDecision,
        /** The decision runs this long after the analysis (for example after a restart). */
        val decidedMinutesLater: Long = 0,
        val hasSpace: Boolean = false,
    )

    private val task = CaptureDecision.Act(SuggestedItemType.Task)
    private val reminder = CaptureDecision.Act(SuggestedItemType.Reminder)
    private val keep = CaptureDecision.Keep
    private fun ask(reason: CaptureOutcomeReason) = CaptureDecision.Ask(reason)

    private val thoughts = listOf(
        // English
        Row("en", "Buy milk tomorrow", task),
        Row("en", "remind me tomorrow", ask(TimeUnclear)),
        Row("en", "remind me about the car", ask(TimeUnclear)),
        Row("en", "remind me about the car", ask(TimeUnclear), hasSpace = true),
        Row("en", "remind me to call mum Tue 9:00", reminder),
        // The local rules read a call with a time as a task for that day.
        Row("en", "call mum Tue 9:00", task),
        Row("en", "remind me in 1 minute", reminder),
        Row("en", "remind me in 1 minute to check the oven", reminder),
        Row("en", "remind me in 1 minute", ask(TimePassed), decidedMinutesLater = 5),
        Row("en", "remind me tomorrow at 9:00 to water plants", reminder),
        Row("en", "remind me tomorrow at 9:00 to water plants", ask(TimePassed), decidedMinutesLater = 2 * 24 * 60),
        Row("en", "remind me in 2 hours to take the laundry out", reminder),
        Row("en", "remind me today at 8:00 to stretch", ask(TimeUnclear)),
        Row("en", "reminder: pay the car tax next week", ask(TimeUnclear)),
        Row("en", "pay rent tomorrow", ask(TimeUnclear)),
        Row("en", "dentist tomorrow", ask(TimeUnclear)),
        Row("en", "call the bank tomorrow", task),
        Row("en", "buy dog food", task),
        Row("en", "send the invoice", keep),
        Row("en", "fix the bike", keep),
        Row("en", "need to renew passport", keep),
        Row("en", "maybe learn photography", keep),
        Row("en", "maybe learn photography", keep, hasSpace = true),
        Row("en", "parking code 4826", keep),
        Row("en", "nice weather", keep),
        Row("en", "book club notes", keep),
        Row("en", "car insurance budget idea", keep, hasSpace = true),
        Row("en", "buy milk\ncall the dentist\nremind me tomorrow at 16:00 to pay rent", keep),
        // Estonian
        Row("et", "osta homme piima", task),
        Row("et", "tuleta mulle homme meelde", ask(TimeUnclear)),
        Row("et", "tuleta meelde auto kohta", ask(TimeUnclear)),
        // A clear time at Medium confidence is confirmed by the user, never set alone.
        Row("et", "tuleta mulle meelde homme kell 9 lilli kasta", ask(TimeUnclear)),
        Row("et", "tuleta mulle 1 minuti pärast meelde", ask(TimeUnclear)),
        Row("et", "tuleta mulle 1 minuti pärast meelde", ask(TimePassed), decidedMinutesLater = 5),
        Row("et", "tuleta mulle 2 tunni pärast meelde pesu välja võtta", ask(TimeUnclear)),
        Row("et", "tuleta mulle meelde eile kell 9 lilli kasta", ask(TimeUnclear)),
        Row("et", "meeldetuletus: auto kindlustus järgmisel nädalal", ask(TimeUnclear)),
        Row("et", "maksa üür homme", ask(TimeUnclear)),
        Row("et", "hambaarst homme", ask(TimeUnclear)),
        Row("et", "helista emale teisipäeval kell 9", task),
        Row("et", "helista pangale homme", task),
        Row("et", "osta koerale toitu", task),
        Row("et", "saada arve", keep),
        Row("et", "paranda jalgratas", keep),
        Row("et", "võib-olla õppida fotograafiat", keep),
        Row("et", "parkimiskood 4826", keep),
        Row("et", "ilus ilm", keep),
        Row("et", "rakenduse idee eelarve jaoks", keep, hasSpace = true),
        // Russian
        Row("ru", "купить молоко завтра", task),
        Row("ru", "напомни мне завтра", ask(TimeUnclear)),
        Row("ru", "напомни про машину", ask(TimeUnclear)),
        Row("ru", "напомни через 1 минуту", reminder),
        Row("ru", "напомни через 1 минуту", ask(TimePassed), decidedMinutesLater = 5),
        Row("ru", "напомни мне завтра в 9:00 полить цветы", reminder),
        Row("ru", "напомни мне завтра в 9:00 полить цветы", ask(TimePassed), decidedMinutesLater = 2 * 24 * 60),
        Row("ru", "напомни через 2 часа вынуть бельё", reminder),
        Row("ru", "напоминание: страховка машины на следующей неделе", ask(TimeUnclear)),
        Row("ru", "оплатить аренду завтра", ask(TimeUnclear)),
        Row("ru", "стоматолог завтра", ask(TimeUnclear)),
        Row("ru", "позвонить маме во вторник в 9:00", task),
        Row("ru", "позвонить в банк завтра", task),
        Row("ru", "купить корм собаке", task),
        Row("ru", "отправить счёт", keep),
        Row("ru", "исправить велосипед", keep),
        Row("ru", "может быть изучить фотографию", keep),
        Row("ru", "код парковки 4826", keep),
        Row("ru", "хорошая погода", keep),
        Row("ru", "идея приложения для бюджета", keep, hasSpace = true),
    )

    @Test
    fun realThoughtsInEveryLanguageGetTheExpectedDecision() {
        assertTrue("at least 60 rows", thoughts.size >= 60)
        assertEquals(setOf("en", "et", "ru"), thoughts.map { it.language }.toSet())
        val wrong = thoughts.mapNotNull { row ->
            val decision = decide(analyse(row), row.hasSpace, decisionTime(row))
            if (decision == row.expected) null else "${row.language} \"${row.text}\": expected ${row.expected}, got $decision"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun lowConfidenceNeverAsksAndNeverFiles() {
        val low = thoughts.filter { analyse(it).confidenceLevel == CaptureConfidence.Low }
        assertTrue("the table has low-confidence rows", low.size >= 6)
        low.forEach { row ->
            assertEquals(row.text, keep, decide(analyse(row), hasSpace = true, now = decisionTime(row)))
        }
    }

    @Test
    fun aReminderIsOnlyEverSetForAClearFutureTime() {
        thoughts.forEach { row ->
            val analysis = analyse(row)
            if (decide(analysis, row.hasSpace, decisionTime(row)) == reminder) {
                assertEquals(row.text, ReminderTimeStatus.Resolved, analysis.reminderTimeStatus)
                assertTrue(row.text, requireNotNull(analysis.suggestedReminderAt) > decisionTime(row))
                assertEquals(row.text, CaptureConfidence.High, analysis.confidenceLevel)
            }
        }
    }

    private data class RuleRow(val name: String, val analysis: CaptureAnalysis, val hasSpace: Boolean, val expected: CaptureDecision)

    private val nowMillis = now.toEpochMilli()

    private fun analysis(
        type: SuggestedItemType,
        confidence: Float,
        status: ReminderTimeStatus = ReminderTimeStatus.Unspecified,
        at: Long? = null,
        reminderPossible: Boolean = status != ReminderTimeStatus.Unspecified,
        failed: Boolean = false,
        brainDump: Boolean = false,
    ) = CaptureAnalysis(
        rawText = "a thought",
        suggestedType = type,
        suggestedSpaceName = "Home",
        suggestedNextAction = "",
        relatedTopics = emptyList(),
        reminderPossible = reminderPossible,
        suggestedReminderAt = at,
        reminderTimeStatus = status,
        confidence = confidence,
        analyzerFailed = failed,
        brainDumpItems = if (brainDump) {
            listOf(
                BrainDumpSuggestion("brain:1", "a", "a", SuggestedItemType.Task, "Home", 0.9f, "", ""),
                BrainDumpSuggestion("brain:2", "b", "b", SuggestedItemType.Note, "Home", 0.9f, "", ""),
            )
        } else {
            emptyList()
        },
    )

    private val high = 0.9f
    private val medium = 0.7f
    private val low = 0.5f
    private val resolved = ReminderTimeStatus.Resolved
    private val unclear = ReminderTimeStatus.NeedsClarification

    private val rules = listOf(
        RuleRow("high reminder, clear future time", analysis(SuggestedItemType.Reminder, high, resolved, nowMillis + 60_000), false, reminder),
        RuleRow("high reminder one millisecond ahead", analysis(SuggestedItemType.Reminder, high, resolved, nowMillis + 1), false, reminder),
        RuleRow("high reminder exactly now", analysis(SuggestedItemType.Reminder, high, resolved, nowMillis), false, ask(TimePassed)),
        RuleRow("high reminder yesterday", analysis(SuggestedItemType.Reminder, high, resolved, nowMillis - 86_400_000), false, ask(TimePassed)),
        RuleRow("high reminder, resolved but no time", analysis(SuggestedItemType.Reminder, high, resolved, null), false, ask(TimeUnclear)),
        RuleRow("high reminder, time unclear", analysis(SuggestedItemType.Reminder, high, unclear), false, ask(TimeUnclear)),
        RuleRow("high reminder, no time at all", analysis(SuggestedItemType.Reminder, high, reminderPossible = true), false, ask(TimeUnclear)),
        RuleRow("unclear status ignores a stray time", analysis(SuggestedItemType.Reminder, high, unclear, nowMillis + 60_000), false, ask(TimeUnclear)),
        RuleRow("medium reminder, clear future time", analysis(SuggestedItemType.Reminder, medium, resolved, nowMillis + 60_000), false, ask(TimeUnclear)),
        RuleRow("medium reminder, past time", analysis(SuggestedItemType.Reminder, medium, resolved, nowMillis - 60_000), false, ask(TimePassed)),
        RuleRow("medium reminder, no time", analysis(SuggestedItemType.Reminder, medium, reminderPossible = true), false, ask(TimeUnclear)),
        RuleRow("low reminder, clear future time", analysis(SuggestedItemType.Reminder, low, resolved, nowMillis + 60_000), false, keep),
        RuleRow("low reminder, time unclear", analysis(SuggestedItemType.Reminder, low, unclear), false, keep),
        RuleRow("low reminder, past time", analysis(SuggestedItemType.Reminder, low, resolved, nowMillis - 60_000), false, keep),
        RuleRow("high task", analysis(SuggestedItemType.Task, high), false, task),
        RuleRow("high task with a reminder cue", analysis(SuggestedItemType.Task, high, unclear), false, task),
        RuleRow("medium task", analysis(SuggestedItemType.Task, medium), false, keep),
        RuleRow("medium task with a reminder cue", analysis(SuggestedItemType.Task, medium, unclear), false, ask(TypeUnclear)),
        RuleRow("low task with a reminder cue", analysis(SuggestedItemType.Task, low, unclear), false, keep),
        RuleRow("high note in a Space", analysis(SuggestedItemType.Note, high), true, CaptureDecision.Act(SuggestedItemType.Note)),
        RuleRow("high note without a Space", analysis(SuggestedItemType.Note, high), false, keep),
        RuleRow("medium note in a Space", analysis(SuggestedItemType.Note, medium), true, keep),
        RuleRow("low note in a Space", analysis(SuggestedItemType.Note, low), true, keep),
        RuleRow("legacy item type", analysis(SuggestedItemType.MondayItem, high), true, keep),
        RuleRow("failed analysis", analysis(SuggestedItemType.Reminder, high, resolved, nowMillis + 60_000, failed = true), true, keep),
        RuleRow("brain dump", analysis(SuggestedItemType.Task, high, brainDump = true), true, keep),
    )

    @Test
    fun eachRuleHolds() {
        val wrong = rules.mapNotNull { row ->
            val decision = decide(row.analysis, row.hasSpace, nowMillis)
            if (decision == row.expected) null else "${row.name}: expected ${row.expected}, got $decision"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    private fun analyse(row: Row): CaptureAnalysis = LocalRulesCaptureAnalyzer(
        now = { now },
        zoneId = { ZoneId.of("Europe/Tallinn") },
        locale = { Locale.forLanguageTag(row.language) },
    ).analyze(row.text)

    private fun decisionTime(row: Row): Long = nowMillis + row.decidedMinutesLater * 60_000L
}
