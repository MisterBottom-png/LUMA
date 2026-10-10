package com.orbit.app.ui.screens.settings

import com.orbit.app.data.export.LocalDataValidationException
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDataToolsMessageTest {
    @Test
    fun `restore completion stays semantic until the selected locale renders it`() {
        assertEquals(
            LocalDataToolsMessage.RestoreCompleted(
                visibleItemCount = 3,
                needsReminderDeviceCheck = true,
            ),
            restoreCompletionMessage(
                visibleItemCount = 3,
                remindersReconciled = false,
            ),
        )
    }

    @Test
    fun `validation failures do not expose an English codec message`() {
        assertEquals(
            LocalDataToolsMessage.RestoreFileInvalid,
            restoreFailureMessage(LocalDataValidationException("Invalid export")),
        )
    }
}
