package com.orbit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRowTest {
    @Test
    fun visibleTitleTrimsUserContent() {
        val title = sourceRowVisibleTitle(
            title = "  Prepare review notes  ",
            fallback = "Untitled task",
        )

        assertEquals("Prepare review notes", title)
    }

    @Test
    fun visibleTitleUsesLocalizedFallbackForBlankContent() {
        val title = sourceRowVisibleTitle(
            title = "   ",
            fallback = "Untitled reminder",
        )

        assertEquals("Untitled reminder", title)
    }
}
