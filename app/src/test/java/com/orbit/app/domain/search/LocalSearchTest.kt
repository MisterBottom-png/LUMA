package com.orbit.app.domain.search

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {
    private val search = LocalSearch()

    @Test
    fun findsFinalizedNote() {
        val results = search.search(
            query = "project outline",
            corpus = SearchCorpus(
                captures = emptyList(),
                notes = listOf(NoteEntity(id = 2, title = "Project outline", body = "Draft sections")),
                tasks = emptyList(),
                reminders = emptyList(),
                spaces = emptyList(),
            ),
        )

        assertEquals(1, results.size)
        assertEquals(ItemDetailType.Note, results.single().type)
    }

    @Test
    fun findsTask() {
        val results = search.search(
            query = "prepare checklist",
            corpus = SearchCorpus(
                captures = emptyList(),
                notes = emptyList(),
                tasks = listOf(TaskEntity(id = 3, title = "Prepare checklist")),
                reminders = emptyList(),
                spaces = emptyList(),
            ),
        )

        assertEquals(ItemDetailType.Task, results.single().type)
    }

    @Test
    fun findsReminder() {
        val results = search.search(
            query = "renew pass",
            corpus = SearchCorpus(
                captures = emptyList(),
                notes = emptyList(),
                tasks = emptyList(),
                reminders = listOf(ReminderEntity(id = 4, title = "Renew pass", dueAt = 1_000L)),
                spaces = emptyList(),
            ),
        )

        assertEquals(ItemDetailType.Reminder, results.single().type)
    }

    @Test
    fun excludesRawAndProcessedCaptures() {
        val results = search.search(
            query = "private source",
            corpus = SearchCorpus(
                captures = listOf(
                    CaptureEntity(id = 1, rawText = "Private source", status = CaptureStatus.Inbox),
                    CaptureEntity(id = 2, rawText = "Private source", status = CaptureStatus.Processed),
                    CaptureEntity(id = 3, rawText = "Private source", status = CaptureStatus.Archived),
                ),
                notes = emptyList(),
                tasks = emptyList(),
                reminders = emptyList(),
                spaces = emptyList(),
            ),
            includeArchived = true,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun linkedCaptureDoesNotDuplicateFinalizedItem() {
        val results = search.search(
            query = "release outline",
            corpus = SearchCorpus(
                captures = listOf(
                    CaptureEntity(
                        id = 5,
                        rawText = "Release outline",
                        status = CaptureStatus.Processed,
                        linkedItemId = 6,
                    ),
                ),
                notes = listOf(NoteEntity(id = 6, title = "Release outline", body = "Final content")),
                tasks = emptyList(),
                reminders = emptyList(),
                spaces = emptyList(),
            ),
        )

        assertEquals(listOf(ItemDetailType.Note), results.map { it.type })
    }

    @Test
    fun preservesArchivedFinalizedItemBehavior() {
        val corpus = SearchCorpus(
            captures = listOf(CaptureEntity(id = 1, rawText = "car source", status = CaptureStatus.Archived)),
            notes = listOf(NoteEntity(id = 2, title = "car note", body = "", archived = true)),
            tasks = listOf(TaskEntity(id = 3, title = "car task", status = TaskStatus.Archived)),
            reminders = emptyList(),
            spaces = listOf(SpaceEntity(id = 4, name = "Car", icon = "directions_car", colorAccent = "#4E91D8", sortOrder = 0)),
        )

        assertTrue(search.search(query = "car", corpus = corpus).isEmpty())

        val archivedResults = search.search(query = "car", corpus = corpus, includeArchived = true)
        assertEquals(setOf(ItemDetailType.Note, ItemDetailType.Task), archivedResults.map { it.type }.toSet())
        assertEquals(2, archivedResults.size)
    }

    @Test
    fun somedayTasksAreSearchableWithCalmStatus() {
        val results = search.search(
            query = "travel",
            corpus = SearchCorpus(
                captures = emptyList(),
                notes = emptyList(),
                tasks = listOf(
                    TaskEntity(
                        id = 7,
                        title = "travel idea",
                        status = TaskStatus.Someday,
                    ),
                ),
                reminders = emptyList(),
                spaces = emptyList(),
            ),
        )

        assertEquals("Someday", results.single().status)
    }

    @Test
    fun emptyAndPartialQueriesReturnNoResults() {
        val corpus = SearchCorpus(
            captures = emptyList(),
            notes = listOf(NoteEntity(id = 8, title = "Plan", body = "Details")),
            tasks = emptyList(),
            reminders = emptyList(),
            spaces = emptyList(),
        )

        assertTrue(search.search(query = "", corpus = corpus).isEmpty())
        assertTrue(search.search(query = "p", corpus = corpus).isEmpty())
    }
}
