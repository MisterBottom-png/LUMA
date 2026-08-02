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
    fun preparesStableFinalizedFeedOrderAndLabels() {
        val contents = SpaceContents(
            notes = listOf(NoteEntity(id = 1, title = "", body = "", updatedAt = 100)),
            tasks = listOf(
                TaskEntity(id = 2, title = "Done task", status = TaskStatus.Done, updatedAt = 300),
            ),
            reminders = listOf(
                ReminderEntity(id = 3, title = "Reminder", dueAt = 200),
            ),
        )

        val feed = contents.asFeedItems(OrbitTimeFormat(uses24HourClock = true))

        assertEquals(
            listOf(
                SpaceItemReference(SpaceItemType.Task, 2),
                SpaceItemReference(SpaceItemType.Reminder, 3),
                SpaceItemReference(SpaceItemType.Note, 1),
            ),
            feed.map { it.reference },
        )
        assertEquals("Task · Done", feed[0].subtitle)
        assertEquals("Untitled note", feed[2].title)
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
