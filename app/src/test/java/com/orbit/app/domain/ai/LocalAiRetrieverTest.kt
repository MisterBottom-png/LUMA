package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAiRetrieverTest {
    @Test
    fun retrieveRanksExactLocalMatches() {
        val work = SpaceEntity(id = 1, name = "Work", icon = "briefcase", colorAccent = "#ffffff", sortOrder = 0)
        val corpus = SearchCorpus(
            captures = emptyList(),
            notes = listOf(
                NoteEntity(id = 7, title = "Change Management decision", body = "Separate from data governance.", spaceId = 1),
            ),
            tasks = listOf(
                TaskEntity(id = 4, title = "Buy dog food", spaceId = null),
            ),
            reminders = emptyList(),
            spaces = listOf(work),
        )

        val result = LocalAiRetriever().retrieve("What did I decide about Change Management?", corpus)

        assertEquals("note:7", result.first().sourceId)
        assertEquals("Work", result.first().spaceName)
    }

    @Test
    fun defaultRetrievalExcludesInternalAndCompletedRows() {
        val corpus = SearchCorpus(
            captures = listOf(CaptureEntity(id = 1, rawText = "Internal source", status = CaptureStatus.Processed)),
            notes = emptyList(),
            tasks = listOf(TaskEntity(id = 2, title = "Finished item", status = TaskStatus.Done)),
            reminders = listOf(ReminderEntity(id = 3, title = "Completed reminder", dueAt = 1L, completedAt = 2L)),
            spaces = emptyList(),
        )

        assertEquals(emptyList<AiSourceItem>(), LocalAiRetriever().recentContext(corpus))
    }

    @Test
    fun commonQueriesUseLocalStateEvenWithoutTitleWordMatches() {
        val now = 10_000L
        val corpus = SearchCorpus(
            captures = emptyList(),
            notes = emptyList(),
            tasks = listOf(
                TaskEntity(id = 4, title = "Renew document", dueAt = now - 1),
                TaskEntity(id = 5, title = "Later task", dueAt = now + 1),
                TaskEntity(id = 6, title = "Waiting item", status = TaskStatus.WaitingFor),
            ),
            reminders = emptyList(),
            spaces = emptyList(),
        )

        assertEquals(listOf("task:4"), LocalAiRetriever().retrieve("What is overdue?", corpus, now = now).map { it.sourceId })
        assertEquals(listOf("task:6"), LocalAiRetriever().retrieve("What is stuck?", corpus, now = now).map { it.sourceId })
    }

    @Test
    fun completedItemsRequireAnExplicitCompletedQuery() {
        val corpus = SearchCorpus(
            captures = emptyList(),
            notes = emptyList(),
            tasks = listOf(TaskEntity(id = 7, title = "Finished item", status = TaskStatus.Done)),
            reminders = emptyList(),
            spaces = emptyList(),
        )

        assertEquals(listOf("task:7"), LocalAiRetriever().retrieve("What did I finish?", corpus).map { it.sourceId })
    }
}
