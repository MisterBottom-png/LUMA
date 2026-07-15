package com.orbit.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNavigationTest {
    @Test
    fun reminderLinksUseReminderSpecificDestination() {
        assertEquals("reminder/42", ReminderDestination.route(42L))
        assertEquals(
            ReminderDestination.route(42L),
            ItemDetailDestination.route(ItemDetailType.Reminder, 42L),
        )
    }

    @Test
    fun noteAndTaskLinksKeepGenericItemDestination() {
        assertEquals("item/note/7", ItemDetailDestination.route(ItemDetailType.Note, 7L))
        assertEquals("item/task/8", ItemDetailDestination.route(ItemDetailType.Task, 8L))
    }
}
