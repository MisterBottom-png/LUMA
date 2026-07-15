package com.orbit.app.ui.components

import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiSourcePresentationTest {
    @Test
    fun `user visible label excludes internal source identifier`() {
        val source = source(sourceId = "task:5", title = "Send revised proposal")

        val label = source.userVisibleLabel()

        assertEquals("Send revised proposal", label)
        assertFalse(label.contains(source.sourceId))
    }

    @Test
    fun `blank title uses human readable type fallback without database id`() {
        val source = source(sourceId = "capture:26", title = "   ", type = ItemDetailType.Capture)

        val label = source.userVisibleLabel()

        assertEquals("Capture", label)
        assertFalse(label.contains("26"))
    }

    private fun source(
        sourceId: String,
        title: String,
        type: ItemDetailType = ItemDetailType.Task,
    ) = AiSourceItem(
        sourceId = sourceId,
        type = type,
        itemId = sourceId.substringAfter(':').toLong(),
        title = title,
        snippet = "",
        spaceName = null,
        status = "",
        timestamp = 0L,
    )
}
