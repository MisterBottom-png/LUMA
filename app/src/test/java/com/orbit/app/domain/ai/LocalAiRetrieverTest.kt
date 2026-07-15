package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
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
}
