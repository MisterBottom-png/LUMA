package com.orbit.app.ui.screens.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Search before typing, and when nothing matches. */
@RunWith(AndroidJUnit4::class)
class SearchContentJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private fun note(id: Long, title: String) =
        LocalSearchResult(ItemDetailType.Note, id, title, "", null, LocalSearchStatus.Note, id)

    @Test
    fun beforeTyping_theNewestSavedItemsShowAndOpen() {
        var opened: LocalSearchResult? = null
        val recent = listOf(note(1, "Parking code"), note(2, "Gift ideas"))
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState(recent = recent),
                    onBack = {},
                    onQueryChanged = {},
                    onIncludeArchivedChanged = {},
                    onResultSelected = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Recently saved").assertExists()
        composeRule.onNodeWithText("Gift ideas").performClick()
        composeRule.runOnIdle { assertEquals(2L, opened?.id) }
    }

    @Test
    fun noMatches_namesTheWords_andOffersArchivedItems() {
        var includeArchived: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState(query = "x"),
                    onBack = {},
                    onQueryChanged = {},
                    onIncludeArchivedChanged = { includeArchived = it },
                    onResultSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("No matches for “x”.").assertExists()
        composeRule.onNode(
            hasText("Search archived too") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        ).performClick()
        composeRule.runOnIdle { assertEquals(true, includeArchived) }
    }

    @Test
    fun theArchivedChip_doesNotOfferTheArchivedSwitchAgain() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState(query = "x", filter = SearchFilter.Archived, includeArchived = true),
                    onBack = {},
                    onQueryChanged = {},
                    onIncludeArchivedChanged = {},
                    onResultSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("No matches for “x”.").assertExists()
        composeRule.onNodeWithText("Search archived too").assertDoesNotExist()
    }
}
