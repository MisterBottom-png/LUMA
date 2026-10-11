package com.orbit.app.ui.screens.spaces

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.time.OrbitTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacesTransformationsTest {
    @Test
    fun systemBackIsHandledOnlyWhileShowingSpaceDetail() {
        assertFalse(shouldHandleSpaceDetailBack(null))
        assertTrue(shouldHandleSpaceDetailBack(space(id = 1, sortOrder = 0)))
    }

    @Test
    fun partitionsSpacesOnceInSortOrder() {
        val activeLater = space(id = 1, sortOrder = 2)
        val hidden = space(id = 2, sortOrder = 1, hidden = true)
        val activeEarlier = space(id = 3, sortOrder = 0)
        val archived = space(id = 4, sortOrder = 3, archived = true)

        val partition = partitionSpaces(
            listOf(activeLater, hidden, archived, activeEarlier),
        )

        assertEquals(listOf(3L, 1L), partition.visible.map { it.id })
        // archived spaces appear in the recovery section
        assertEquals(listOf(4L), partition.archived.map { it.id })
        // hidden spaces are truly invisible until the user expands the disclosure row
        assertEquals(listOf(2L), partition.hidden.map { it.id })
    }

    @Test
    fun countsFinalizedVisibleItemsInOnePassWithOriginalRules() {
        val spaces = listOf(space(id = 1, sortOrder = 0), space(id = 2, sortOrder = 1))
        val counts = calculateSpaceItemCounts(
            spaces = spaces,
            notes = listOf(
                NoteEntity(id = 1, title = "Note", body = "", spaceId = 1),
                NoteEntity(id = 2, title = "Archived", body = "", spaceId = 1, archived = true),
            ),
            tasks = listOf(
                TaskEntity(id = 1, title = "Task", spaceId = 1),
                TaskEntity(id = 2, title = "Done", spaceId = 2, status = TaskStatus.Done),
                TaskEntity(id = 3, title = "Archived", spaceId = 2, status = TaskStatus.Archived),
            ),
            reminders = listOf(
                ReminderEntity(id = 1, title = "Reminder", dueAt = 100, spaceId = 2),
                ReminderEntity(id = 2, title = "Outside", dueAt = 200, spaceId = 99),
            ),
        )

        assertEquals(linkedMapOf(1L to 2, 2L to 2), counts)
    }

    @Test
    fun agendaSplitsByDay_untimedTodayFirst_doneFoldedAway() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 10, 10)
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        fun at(day: java.time.LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()
        val contents = SpaceContents(
            notes = listOf(NoteEntity(id = 9, title = "Ideas", body = "")),
            tasks = listOf(
                TaskEntity(id = 1, title = "Report", dueAt = at(today, 11)),
                TaskEntity(id = 2, title = "Stretch", scheduledDateEpochDay = today.toEpochDay()),
                TaskEntity(id = 3, title = "Car service", dueAt = at(today.minusDays(2), 10)),
                TaskEntity(id = 4, title = "Slides", dueAt = at(today.plusDays(3), 9)),
                TaskEntity(id = 5, title = "Tap", updatedAt = 5),
                TaskEntity(id = 6, title = "Finished", status = TaskStatus.Done, completedAt = 7, dueAt = at(today, 9)),
            ),
            reminders = listOf(ReminderEntity(id = 7, title = "Stand-up", dueAt = at(today, 14))),
        )

        val agenda = contents.agenda(now, zone)

        assertEquals(listOf("Stretch", "Report", "Stand-up"), agenda.today.map { it.title })
        assertEquals(listOf("Car service"), agenda.earlier.map { it.title })
        assertEquals(listOf("Slides"), agenda.upcoming.map { it.title })
        assertEquals(listOf("Tap"), agenda.noDate.map { it.title })
        assertEquals(listOf("Finished"), agenda.done.map { it.title })
        assertEquals(listOf(9L), agenda.notes.map { it.id })
        assertFalse(agenda.today.first().hasTime)
    }

    @Test
    fun agendaKeepsAnItemTickedOnThisVisitInItsDay() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 10, 10)
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        val done = TaskEntity(
            id = 1,
            title = "Report",
            status = TaskStatus.Done,
            completedAt = now,
            dueAt = today.atTime(11, 0).toInstant(zone).toEpochMilli(),
        )

        val agenda = SpaceContents(tasks = listOf(done))
            .agenda(now, zone, keepInPlace = setOf(SpaceItemReference(SpaceItemType.Task, 1)))

        assertEquals(listOf("Report"), agenda.today.map { it.title })
        assertTrue(agenda.today.single().isDone)
        assertTrue(agenda.done.isEmpty())
    }

    @Test
    fun openCountsLeaveOutNotesDoneAndHandledItems() {
        val counts = calculateSpaceOpenCounts(
            spaces = listOf(com.orbit.app.data.local.entity.SpaceEntity(id = 4, name = "Work", icon = "work", colorAccent = "#6D7CFF", sortOrder = 0)),
            tasks = listOf(
                TaskEntity(id = 1, title = "Open", spaceId = 4),
                TaskEntity(id = 2, title = "Done", spaceId = 4, status = TaskStatus.Done),
            ),
            reminders = listOf(
                ReminderEntity(id = 3, title = "Ahead", spaceId = 4, dueAt = 10),
                ReminderEntity(id = 4, title = "Handled", spaceId = 4, dueAt = 10, completedAt = 11),
            ),
        )

        assertEquals(2, counts[4L])
    }

    @Test
    fun unfiledEntryAppearsOnlyForActiveFinalizedItemsWithoutASpace() {
        assertTrue(
            hasUnfiledFinalizedItems(
                notes = listOf(NoteEntity(id = 1, title = "Unfiled", body = "")),
                tasks = emptyList(),
                reminders = emptyList(),
            ),
        )
        assertFalse(
            hasUnfiledFinalizedItems(
                notes = listOf(NoteEntity(id = 2, title = "Archived", body = "", archived = true)),
                tasks = listOf(TaskEntity(id = 3, title = "Archived", status = TaskStatus.Archived)),
                reminders = emptyList(),
            ),
        )
    }

    @Test
    fun spaceNamesUseCollapsedCaseInsensitiveUniqueness() {
        val existing = listOf(space(id = 1, sortOrder = 0).copy(name = "Home Base"))

        assertEquals("home base", normalizeSpaceName("  HOME   base  "))
        assertFalse(canUseSpaceName("home base", existing))
        assertTrue(canUseSpaceName("home base", existing, excludingSpaceId = 1))
        assertTrue(canUseSpaceName("Learning", existing))
    }

    private fun space(
        id: Long,
        sortOrder: Int,
        hidden: Boolean = false,
        archived: Boolean = false,
    ) = SpaceEntity(
        id = id,
        name = "Space $id",
        icon = "folder",
        colorAccent = "#6D7CFF",
        sortOrder = sortOrder,
        hidden = hidden,
        archived = archived,
    )
}
