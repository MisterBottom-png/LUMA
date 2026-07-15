package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRulesSituationAnalyzerTest {
    private val analyzer = LocalRulesSituationAnalyzer()
    private val now = 1_800_000_000_000L

    @Test
    fun `summarises every local source and prioritises overdue reminders`() {
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

        assertTrue(result.whereYouAre.contains("1 inbox capture"))
        assertTrue(result.whereYouAre.contains("1 active task"))
        assertTrue(result.whereYouAre.contains("1 reminder"))
        assertTrue(result.whereYouAre.contains("1 note"))
        assertEquals("Overdue reminder: Call the dentist", result.nextAction)
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
        assertTrue(result.nextAction.contains("real pause"))
        assertEquals("There is no obvious local noise to clear right now.", result.clearNoiseSuggestion)
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
