package com.orbit.app.ui.screens.review

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewRowInteractionTest {
    @Test
    fun `review row click forwards the exact item for every review item type`() {
        ReviewItemType.entries.forEachIndexed { index, type ->
            val item = ReviewItem(
                id = index + 1L,
                type = type,
                title = type.name,
                timestamp = 100L + index,
            )
            var selected: ReviewItem? = null

            reviewRowClick(item) { selected = it }.invoke()

            assertEquals(item, selected)
        }
    }
}
