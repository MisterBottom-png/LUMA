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
    fun aThoughtStillToSortIsFoundOnce_byAWordFromItsMiddleOrEnd_butNotOnceSortedOrLetGo() {
        val work = SpaceEntity(id = 9, name = "Work", icon = "work", colorAccent = "#4E91D8", sortOrder = 0)
        val corpus = SearchCorpus(
            captures = listOf(
                CaptureEntity(id = 1, rawText = "Ask about the boiler service\nreference 4826-ZX", status = CaptureStatus.Inbox, suggestedSpaceId = 9),
                CaptureEntity(id = 2, rawText = "boiler filed already", status = CaptureStatus.Processed, linkedItemId = 3),
                CaptureEntity(id = 3, rawText = "boiler let go", status = CaptureStatus.Archived),
            ),
            notes = emptyList(),
            tasks = emptyList(),
            reminders = emptyList(),
            spaces = listOf(work),
        )

        val byEnd = search.search(query = "4826", corpus = corpus).single()
        assertEquals(ItemDetailType.Capture, byEnd.type)
        assertEquals(1L, byEnd.id)
        assertEquals(LocalSearchStatus.ToSort, byEnd.status)
        assertEquals("Ask about the boiler service", byEnd.title)
        assertEquals("Work", byEnd.spaceName)

        assertEquals(listOf(1L), search.search(query = "boiler", corpus = corpus).map { it.id })
        assertEquals(listOf(1L), search.search(query = "boiler", corpus = corpus, includeArchived = true).map { it.id })
        assertEquals(listOf(1L), search.recent(corpus).map { it.id })
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

        assertEquals(LocalSearchStatus.Someday, results.single().status)
    }

    @Test
    fun emptyQueryReturnsNothing_andOneLetterAlreadyFinds() {
        val corpus = SearchCorpus(
            captures = emptyList(),
            notes = listOf(NoteEntity(id = 8, title = "Plan", body = "Details")),
            tasks = emptyList(),
            reminders = emptyList(),
            spaces = emptyList(),
        )

        assertTrue(search.search(query = "", corpus = corpus).isEmpty())
        assertTrue(search.search(query = "  ", corpus = corpus).isEmpty())
        assertEquals(listOf(8L), search.search(query = "p", corpus = corpus).map { it.id })
        assertTrue(search.search(query = "z", corpus = corpus).isEmpty())
    }

    @Test
    fun recentShowsTheFiveNewestSavedItems_includingAThoughtStillToSort_butNotArchived() {
        val corpus = SearchCorpus(
            captures = listOf(
                CaptureEntity(id = 50, rawText = "Kept thought", status = CaptureStatus.Inbox, createdAt = 99),
                CaptureEntity(id = 51, rawText = "Filed thought", status = CaptureStatus.Processed, createdAt = 98),
            ),
            notes = listOf(
                NoteEntity(id = 1, title = "Oldest note", body = "", createdAt = 1),
                NoteEntity(id = 2, title = "Archived note", body = "", createdAt = 90, archived = true),
                NoteEntity(id = 3, title = "Newest note", body = "", createdAt = 80),
            ),
            tasks = listOf(
                TaskEntity(id = 4, title = "Task", createdAt = 70),
                TaskEntity(id = 5, title = "Archived task", createdAt = 95, status = TaskStatus.Archived),
                TaskEntity(id = 6, title = "Done task", createdAt = 40, status = TaskStatus.Done),
            ),
            reminders = listOf(
                ReminderEntity(id = 7, title = "Reminder", dueAt = 10_000, createdAt = 60),
                ReminderEntity(id = 8, title = "Older reminder", dueAt = 20_000, createdAt = 5),
            ),
            spaces = emptyList(),
        )

        val recent = search.recent(corpus)

        assertEquals(listOf("Kept thought", "Newest note", "Task", "Reminder", "Done task"), recent.map { it.title })
        assertTrue(recent.none { it.status == LocalSearchStatus.Archived })
        assertEquals(2, search.recent(corpus, limit = 2).size)
    }

    private fun notes(vararg titles: String) = SearchCorpus(
        captures = emptyList(),
        notes = titles.mapIndexed { index, title -> NoteEntity(id = index + 1L, title = title, body = "") },
        tasks = emptyList(),
        reminders = emptyList(),
        spaces = emptyList(),
    )

    @Test
    fun unicodeFormsCaseAndYoMatchInEveryLanguage() {
        val corpus = notes("Cafe\u0301 menüü", "Ёлка на праздник", "ÕUNAD turult")

        assertEquals(listOf(1L), search.search("café", corpus).map { it.id })
        assertEquals(listOf(1L), search.search("MENÜÜ", corpus).map { it.id })
        assertEquals(listOf(2L), search.search("елка", corpus).map { it.id })
        assertEquals(listOf(3L), search.search("õunad", corpus).map { it.id })
    }

    @Test
    fun everyWordMustAppear_inAnyOrder_ignoringPunctuation() {
        val corpus = notes("Call the dentist about «Tuesday»", "Call mum")

        assertEquals(listOf(1L), search.search("tuesday, dentist", corpus).map { it.id })
        assertTrue(search.search("dentist friday", corpus).isEmpty())
    }
}
