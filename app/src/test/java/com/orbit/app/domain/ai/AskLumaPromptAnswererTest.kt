package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AskLumaPromptAnswererTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private val empty = SearchCorpus(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun nothingNeededIsAValidCompleteAnswer() {
        AskLumaQuestion.entries.forEach { question ->
            val answer = AskLumaPromptAnswerer.answer(question, empty, now, zone)
            assertEquals(question.name, true, answer.items.isEmpty())
        }
        assertEquals(AskLumaAnswerKind.NothingNeeded, AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, empty, now, zone).kind)
        assertEquals(AskLumaAnswerKind.NothingUrgent, AskLumaPromptAnswerer.answer(AskLumaQuestion.AnythingUrgent, empty, now, zone).kind)
    }

    @Test
    fun whatNowStartsWithTheNextThingDueToday() {
        val corpus = empty.copy(
            reminders = listOf(ReminderEntity(id = 1, title = "Pick up parcel", dueAt = now + 3_600_000)),
            tasks = listOf(TaskEntity(id = 2, title = "Someday thing", status = TaskStatus.Someday)),
        )
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.StartWith, answer.kind)
        assertEquals("Pick up parcel", answer.items.single().title)
    }

    @Test
    fun withNothingDueWhatNowGentlyMentionsThoughtsToSort() {
        val corpus = empty.copy(captures = listOf(CaptureEntity(id = 1, rawText = "x"), CaptureEntity(id = 2, rawText = "y")))
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.SortThoughts, answer.kind)
        assertEquals(2, answer.count)
    }

    @Test
    fun dependsOnOthersListsWaitingTasks() {
        val corpus = empty.copy(tasks = listOf(TaskEntity(id = 3, title = "Reply from landlord", status = TaskStatus.WaitingFor)))
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.DependsOnOthers, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.WaitingOnOthers, answer.kind)
    }

    @Test
    fun urgentOnlyCoversTheNextFewHours() {
        val corpus = empty.copy(
            reminders = listOf(
                ReminderEntity(id = 1, title = "Soon", dueAt = now + 60 * 60_000),
                ReminderEntity(id = 2, title = "Next week", dueAt = now + 7L * 86_400_000L),
            ),
        )
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.AnythingUrgent, corpus, now, zone)
        assertEquals(listOf("Soon"), answer.items.map { it.title })
    }

    private val dayMillis = 86_400_000L

    @Test
    fun anEmptyDatabaseNeedsNothing() {
        assertEquals(AskLumaAnswerKind.NothingNeeded, AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, empty, now, zone).kind)
        assertEquals(AskLumaAnswerKind.NothingCanWait, AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatCanWait, empty, now, zone).kind)
        assertEquals(AskLumaAnswerKind.NothingWaiting, AskLumaPromptAnswerer.answer(AskLumaQuestion.DependsOnOthers, empty, now, zone).kind)
    }

    @Test
    fun anOverdueTaskIsMentionedInsteadOfSayingNothingNeedsYou() {
        val corpus = empty.copy(tasks = listOf(TaskEntity(id = 1, title = "Pay the bill", dueAt = now - 2 * dayMillis)))
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.FromEarlier, answer.kind)
        assertEquals(1, answer.count)
        assertEquals(listOf("Pay the bill"), answer.items.map { it.title })
    }

    @Test
    fun aTaskForAnEarlierDayAndAnOldReminderAreFromEarlierToo() {
        val yesterday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(1).toEpochDay()
        val corpus = empty.copy(
            tasks = listOf(TaskEntity(id = 1, title = "Water plants", scheduledDateEpochDay = yesterday)),
            reminders = listOf(ReminderEntity(id = 2, title = "Call back", dueAt = now - 3 * dayMillis)),
        )
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.FromEarlier, answer.kind)
        assertEquals(2, answer.count)
    }

    @Test
    fun undatedTasksAreListedSeparatelyUnderNoDateSet() {
        val corpus = empty.copy(
            tasks = listOf(
                TaskEntity(id = 1, title = "Sort photos"),
                TaskEntity(id = 2, title = "Book flights", dueAt = now + 5 * dayMillis),
            ),
        )
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatCanWait, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.CanWait, answer.kind)
        assertEquals(listOf("Book flights"), answer.items.map { it.title })
        assertEquals(listOf("Sort photos"), answer.undated.map { it.title })
        assertEquals(listOf("Book flights", "Sort photos"), answer.sources.map { it.title })
    }

    @Test
    fun aWaitingItemIsNamedAndIsNotCountedAsEarlier() {
        val corpus = empty.copy(tasks = listOf(TaskEntity(id = 1, title = "Reply from landlord", status = TaskStatus.WaitingFor)))
        assertEquals(
            listOf("Reply from landlord"),
            AskLumaPromptAnswerer.answer(AskLumaQuestion.DependsOnOthers, corpus, now, zone).items.map { it.title },
        )
        assertEquals(AskLumaAnswerKind.NothingNeeded, AskLumaPromptAnswerer.answer(AskLumaQuestion.WhatNow, corpus, now, zone).kind)
    }

    @Test
    fun urgentUsesTheSameWindowForTasksAndReminders() {
        val anHourAgo = now - 3_600_000L
        val corpus = empty.copy(
            tasks = listOf(TaskEntity(id = 1, title = "Send form", dueAt = anHourAgo)),
            reminders = listOf(ReminderEntity(id = 2, title = "Take medicine", dueAt = anHourAgo)),
        )
        val answer = AskLumaPromptAnswerer.answer(AskLumaQuestion.AnythingUrgent, corpus, now, zone)
        assertEquals(AskLumaAnswerKind.Urgent, answer.kind)
        assertEquals(setOf("Send form", "Take medicine"), answer.items.map { it.title }.toSet())
    }
}
