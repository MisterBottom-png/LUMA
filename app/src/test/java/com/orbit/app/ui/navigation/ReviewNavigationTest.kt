package com.orbit.app.ui.navigation

import com.orbit.app.ui.screens.review.ReviewItem
import com.orbit.app.ui.screens.review.ReviewItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewNavigationTest {
    @Test
    fun reviewItems_routeToExistingDetailDestinationsWithTheirOriginalIds() {
        assertEquals(
            ItemDetailDestination.route(ItemDetailType.Task, 11L),
            reviewItem(ReviewItemType.Task, 11L).toItemDetailRoute(),
        )
        assertEquals(
            ItemDetailDestination.route(ItemDetailType.Capture, 22L),
            reviewItem(ReviewItemType.Capture, 22L).toItemDetailRoute(),
        )
        assertEquals(
            ReminderDestination.route(33L),
            reviewItem(ReviewItemType.Reminder, 33L).toItemDetailRoute(),
        )
    }

    private fun reviewItem(type: ReviewItemType, id: Long) = ReviewItem(
        id = id,
        type = type,
        title = "Review item",
        timestamp = 1L,
    )
}
