package com.orbit.app.ui.screens.search

import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFeedbackStateTest {
    @Test
    fun blankQueryShowsTypingGuidanceRatherThanNoResults() {
        assertEquals(SearchFeedbackState.StartTyping, SearchUiState().feedbackState())
        assertEquals(SearchFeedbackState.StartTyping, SearchUiState(query = "   ").feedbackState())
    }

    @Test
    fun shortAndUnmatchedQueriesRemainDistinctFromTypingGuidance() {
        assertEquals(SearchFeedbackState.MinimumQuery, SearchUiState(query = "a").feedbackState())
        assertEquals(SearchFeedbackState.EmptyResults, SearchUiState(query = "absent").feedbackState())
    }

    @Test
    fun matchingQueryShowsResults() {
        val result = LocalSearchResult(
            type = ItemDetailType.Note,
            id = 1L,
            title = "A note",
            snippet = "A note",
            spaceName = null,
            status = LocalSearchStatus.Note,
            timestamp = 1L,
        )

        assertEquals(
            SearchFeedbackState.Results,
            SearchUiState(query = "note", results = listOf(result)).feedbackState(),
        )
    }
}
