package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.util.concurrent.TimeUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRulesSituationAnalyzerTest {
    private val analyzer = LocalRulesSituationAnalyzer()
    private val now = 1_800_000_000_000L

    @Test
    fun `surfaces a few grounded items instead of database counts`() {
        val result = analyzer.analyze(
            snapshot(
                captures = listOf(CaptureEntity(id = 1, rawText = "Sort trip ideas", createdAt = now)),
                notes = listOf(NoteEntity(id = 2, title = "Trip", body = "Ideas")),
                tasks = listOf(TaskEntity(id = 3, title = "Book train")),
                reminders = listOf(
                    ReminderEntity(id = 4, title = "Call the dentist", dueAt = now - 1_000),
                ),
            ),
        )

        assertTrue(result.whereYouAre.contains("Call the dentist"))
        assertTrue(result.whereYouAre.contains("overdue", ignoreCase = true))
        assertTrue(result.whatMatters.size <= 3)
        assertTrue(result.whatMatters.all { line ->
            listOf("Overdue", "Due soon", "Recently captured", "Recently updated").any(line::startsWith)
        })
        assertTrue(result.nextAction.startsWith("Overdue reminder: Call the dentist"))
        assertTrue("reminder:4" in result.sourceItemIds)
    }

    @Test
    fun `reports waiting for and stale loops without changing them`() {
        val old = now - TimeUnit.DAYS.toMillis(8)
        val waiting = TaskEntity(
            id = 1,
            title = "manager to approve the draft",
            status = TaskStatus.WaitingFor,
            updatedAt = old,
        )
        val staleCapture = CaptureEntity(
            id = 2,
            rawText = "Old loose thought",
            status = CaptureStatus.Inbox,
            createdAt = old,
            updatedAt = old,
        )

        val result = analyzer.analyze(snapshot(tasks = listOf(waiting), captures = listOf(staleCapture)))

        assertTrue(result.whatIsStuck.any { it.contains("manager to approve") })
        assertTrue(result.whatIsStuck.any { it.contains("Old loose thought") })
        assertTrue(result.clearNoiseSuggestion.contains("nothing will be changed automatically"))
        assertEquals(TaskStatus.WaitingFor, waiting.status)
        assertEquals(CaptureStatus.Inbox, staleCapture.status)
    }

    @Test
    fun `provides a calm fallback when local data is empty`() {
        val result = analyzer.analyze(snapshot())

        assertTrue(result.whatMatters.single().contains("Nothing urgent"))
        assertTrue(result.whatIsStuck.single().contains("No waiting-for"))
        assertEquals("No local item needs a next step right now.", result.nextAction)
        assertEquals("There is no obvious local noise to clear right now.", result.clearNoiseSuggestion)
    }

    @Test
    fun `excludes completed archived and processed items`() {
        val result = analyzer.analyze(
            snapshot(
                captures = listOf(
                    CaptureEntity(id = 1, rawText = "Processed source", status = CaptureStatus.Processed, createdAt = now),
                    CaptureEntity(id = 2, rawText = "Archived source", status = CaptureStatus.Archived, createdAt = now),
                ),
                notes = listOf(NoteEntity(id = 3, title = "Archived note", body = "", archived = true)),
                tasks = listOf(
                    TaskEntity(id = 4, title = "Done task", status = TaskStatus.Done, completedAt = now),
                    TaskEntity(id = 5, title = "Archived task", status = TaskStatus.Archived),
                ),
                reminders = listOf(ReminderEntity(id = 6, title = "Completed reminder", dueAt = now - 1, completedAt = now)),
            ),
        )

        assertEquals("No active local item needs immediate attention right now.", result.whereYouAre)
        assertTrue(result.sourceItemIds.isEmpty())
        assertTrue(result.whatMatters.single().startsWith("Nothing urgent"))
    }

    @Test
    fun `estonian local guidance uses the selected locale`() {
        val result = LocalRulesSituationAnalyzer(
            locale = { Locale.forLanguageTag("et") },
        ).analyze(snapshot())

        assertEquals("Ükski kohalik asi ei vaja praegu kohe tähelepanu.", result.whereYouAre)
        assertTrue(result.whatMatters.single().startsWith("Praegu"))
        assertTrue(result.clearNoiseSuggestion.startsWith("Praegu"))
    }

    @Test
    fun `russian local guidance preserves source text without translating it`() {
        val source = "Keep this exact title"
        val result = LocalRulesSituationAnalyzer(
            locale = { Locale.forLanguageTag("ru") },
        ).analyze(snapshot(captures = listOf(CaptureEntity(id = 1, rawText = source, createdAt = now))))

        assertTrue(result.whatMatters.single().startsWith("Недавно сохранено"))
        assertTrue(result.whatMatters.single().contains(source))
        assertTrue(result.clearNoiseSuggestion.startsWith("Начните"))
    }

    private fun snapshot(
        captures: List<CaptureEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
        tasks: List<TaskEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
    ) = SituationSnapshot(
        captures = captures,
        notes = notes,
        tasks = tasks,
        reminders = reminders,
        staleLoopDays = 7,
        now = now,
    )
}
