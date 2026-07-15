package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReviewAnalyzerTest {
    private val analyzer = LocalReviewAnalyzer()
    private val now = 1_800_000_000_000L

    @Test
    fun `finds stale active tasks and inbox captures`() {
        val eightDaysAgo = now - TimeUnit.DAYS.toMillis(8)
        val tasks = listOf(
            TaskEntity(id = 1, title = "Open", updatedAt = eightDaysAgo),
            TaskEntity(id = 2, title = "Waiting", status = TaskStatus.WaitingFor, updatedAt = eightDaysAgo),
            TaskEntity(id = 3, title = "Done", status = TaskStatus.Done, updatedAt = eightDaysAgo),
            TaskEntity(id = 6, title = "Someday", status = TaskStatus.Someday, updatedAt = eightDaysAgo),
        )
        val captures = listOf(
            CaptureEntity(id = 4, rawText = "Inbox", updatedAt = eightDaysAgo),
            CaptureEntity(
                id = 5,
                rawText = "Archived",
                status = CaptureStatus.Archived,
                updatedAt = eightDaysAgo,
            ),
        )

        val stale = analyzer.findStaleLoops(tasks, captures, staleLoopDays = 7, now = now)

        assertEquals(listOf("Task_1", "Task_2", "Capture_4"), stale.map { it.key })
    }

    @Test
    fun `does not mark recently updated loops stale`() {
        val sixDaysAgo = now - TimeUnit.DAYS.toMillis(6)

        val stale = analyzer.findStaleLoops(
            tasks = listOf(TaskEntity(id = 1, title = "Recent", updatedAt = sixDaysAgo)),
            captures = emptyList(),
            staleLoopDays = 7,
            now = now,
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `make smaller returns one local concrete suggestion`() {
        val suggestion = analyzer.makeSmaller(
            ReviewLoop(
                id = 1,
                type = ReviewLoopType.Task,
                title = "Call manager about the report",
                updatedAt = now,
            ),
        )

        assertEquals("Task_1", suggestion.sourceKey)
        assertEquals(
            "Write one sentence you want to say, then decide whether to send it.",
            suggestion.action,
        )
    }

    @Test
    fun `make smaller handles vague money and car examples locally`() {
        assertEquals(
            "Check one small expense category.",
            LocalReviewAnalyzer.makeSmallerText("fix money situation"),
        )
        assertEquals(
            "Write down when the sound happens.",
            LocalReviewAnalyzer.makeSmallerText("Sort car problem"),
        )
        assertEquals(
            "List the first three questions.",
            LocalReviewAnalyzer.makeSmallerText("Prepare change management project"),
        )
    }
}
