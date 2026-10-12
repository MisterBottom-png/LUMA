package com.orbit.app.ui.screens.item

import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each kind of item shows only the details that mean something for it. */
class ItemDetailFieldsTest {
    @Test
    fun aNoteShowsItsTypeDateAndSpace_withNoStatusOrReminder() {
        // F14: a note can be given a date ("Add date"), not only keep one it already has.
        val fields = itemDetailFields(ItemDetailType.Note, hasNotificationSettings = false)

        assertEquals(listOf(ItemDetailField.Type, ItemDetailField.Schedule, ItemDetailField.Space), fields.shown)
        assertTrue(fields.underMore.isEmpty())
    }

    @Test
    fun aTaskKeepsItsDateAndStatusUnderMore() {
        val fields = itemDetailFields(ItemDetailType.Task, hasNotificationSettings = false)

        assertEquals(listOf(ItemDetailField.Type, ItemDetailField.Space), fields.shown)
        assertEquals(listOf(ItemDetailField.Schedule, ItemDetailField.LifeState), fields.underMore)
    }

    @Test
    fun aReminderShowsItsTimeAndAlert_butNoTaskStatus() {
        val fields = itemDetailFields(ItemDetailType.Reminder, hasNotificationSettings = true)

        assertEquals(
            listOf(
                ItemDetailField.Type,
                ItemDetailField.Schedule,
                ItemDetailField.Notification,
                ItemDetailField.Repeat,
                ItemDetailField.Space,
            ),
            fields.shown,
        )
        assertTrue(fields.underMore.isEmpty())
        assertEquals(
            listOf(ItemDetailField.Type, ItemDetailField.Schedule, ItemDetailField.Space),
            itemDetailFields(ItemDetailType.Reminder, hasNotificationSettings = false).shown,
        )
    }

    @Test
    fun aRawThoughtHasNoDetailRows() {
        assertTrue(itemDetailFields(ItemDetailType.Capture, hasNotificationSettings = false).shown.isEmpty())
    }
}
