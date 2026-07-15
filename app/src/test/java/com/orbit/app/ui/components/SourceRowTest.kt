package com.orbit.app.ui.components

import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRowTest {
    @Test
    fun accessibilityTextIncludesHumanReadableTypeTitleDateAndAction() {
        val description = sourceRowAccessibilityText(
            title = "Prepare review notes",
            itemType = ItemDetailType.Task,
            dateTimeText = "Tomorrow, 09:30",
        )

        assertEquals("Task: Prepare review notes, Tomorrow, 09:30. Open", description)
    }

    @Test
    fun accessibilityTextUsesReadableFallbackWithoutInternalIdentifier() {
        val description = sourceRowAccessibilityText(
            title = "   ",
            itemType = ItemDetailType.Reminder,
            dateTimeText = null,
        )

        assertEquals("Reminder: Untitled reminder. Open", description)
    }
}
