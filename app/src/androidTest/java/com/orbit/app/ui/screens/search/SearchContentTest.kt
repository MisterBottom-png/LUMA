package com.orbit.app.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButtonInvokesTheNavigationCallbackOnce() {
        var backInvocations = 0
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState(),
                    onBack = { backInvocations += 1 },
                    onQueryChanged = {},
                    onIncludeArchivedChanged = {},
                    onResultSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backInvocations)
        }
    }

    @Test
    fun constrainedWidthAndLargeTextKeepSearchLandmarksAvailable() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        SearchContent(
                            state = SearchUiState(),
                            onBack = {},
                            onQueryChanged = {},
                            onIncludeArchivedChanged = {},
                            onResultSelected = {},
                        )
                    }
                }
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Search"),
        ).assertExists()
        composeRule.onNode(isHeading()).assertExists()
        composeRule.onNodeWithText("Search local data").assertIsDisplayed()
    }
}
