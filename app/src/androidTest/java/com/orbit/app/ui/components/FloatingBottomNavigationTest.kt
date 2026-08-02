package com.orbit.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.navigation.OrbitDestination
import org.junit.Rule
import org.junit.Test

class FloatingBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destinationsExposeTabRoleSelectionAndMinimumTouchTarget() {
        composeRule.setContent {
            MaterialTheme {
                FloatingBottomNavigation(
                    selectedRoute = OrbitDestination.Home.route,
                    onDestinationSelected = {},
                    onSituationAiSelected = {},
                    situationAiFocusRequester = remember { FocusRequester() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Home")
            .assertIsSelected()
            .assertHasTabRole()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        listOf("Spaces", "Review", "Settings").forEach { label ->
            composeRule.onNodeWithContentDescription(label)
                .assertIsNotSelected()
                .assertHasTabRole()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun situationAiHasPreciseLabelAndMinimumTouchTarget() {
        composeRule.setContent {
            MaterialTheme {
                FloatingBottomNavigation(
                    selectedRoute = OrbitDestination.Home.route,
                    onDestinationSelected = {},
                    onSituationAiSelected = {},
                    situationAiFocusRequester = remember { FocusRequester() },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Situation AI")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHasTabRole() =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
}
