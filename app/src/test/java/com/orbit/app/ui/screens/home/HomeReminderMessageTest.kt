package com.orbit.app.ui.screens.home

import com.orbit.app.reminders.ReminderSaveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeReminderMessageTest {
    @Test
    fun onlyAReminderThatWillRingSaysReminderSet() {
        assertEquals(HomeMessage.ReminderSet, ReminderSaveOutcome.Saved.toHomeMessage())
        ReminderSaveOutcome.entries.filterNot { it == ReminderSaveOutcome.Saved }.forEach { outcome ->
            assertNotEquals(outcome.name, HomeMessage.ReminderSet, outcome.toHomeMessage())
            assertNotEquals(outcome.name, HomeMessage.ReminderSet.textRes, outcome.toHomeMessage().textRes)
        }
        assertEquals(HomeMessage.ReminderNotificationsOff, ReminderSaveOutcome.SavedNotificationsBlocked.toHomeMessage())
    }
}
