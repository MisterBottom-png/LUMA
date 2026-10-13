package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import com.orbit.app.testing.JvmComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.ui.navigation.CalendarDestination
import com.orbit.app.ui.navigation.OrbitDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The tab bar on the JVM, including large text in a narrow window. */
@RunWith(AndroidJUnit4::class)
class FloatingBottomNavigationJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun fourLabelledTabs_calendarSelectedByItsRoute_andClicksReportTheDestination() {
        var selected: OrbitDestination? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        FloatingBottomNavigation(
                            selectedRoute = CalendarDestination.Route,
                            onDestinationSelected = { selected = it },
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertCountEquals(4)
        composeRule.onNodeWithText("Calendar").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Settings").assertDoesNotExist()

        composeRule.onNodeWithText("Needs you").performClick()
        assertEquals(OrbitDestination.Review, selected)
    }

    @Test
    fun aSpacesOwnPageKeepsTheSpacesTabSelected() {
        assertTrue(isBottomBarDestinationSelected("spaces/{spaceId}", OrbitDestination.Spaces))
        assertTrue(isBottomBarDestinationSelected("spaces/unfiled", OrbitDestination.Spaces))
        assertTrue(isBottomBarDestinationSelected("spaces", OrbitDestination.Spaces))
        org.junit.Assert.assertFalse(isBottomBarDestinationSelected("spaces/{spaceId}", OrbitDestination.Home))
    }

    @Test
    fun needsYouShowsADotWhenSomethingWaits_redOnlyWhenBlocked_neverACount() {
        var failed by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    FloatingBottomNavigation(
                        selectedRoute = OrbitDestination.Home.route,
                        onDestinationSelected = {},
                        alertDestinations = if (failed) setOf(OrbitDestination.Review) else emptySet(),
                        blockedDestinations = if (failed) setOf(OrbitDestination.Review) else emptySet(),
                    )
                }
            }
        }
        // N028: the dot is named inside the tab, so the tab's own "Selected" state stays.
        val alert = androidx.compose.ui.test.hasContentDescription("Something didn’t work", substring = true)
        composeRule.onAllNodes(alert, useUnmergedTree = true).assertCountEquals(0)

        failed = true
        composeRule.waitForIdle()
        composeRule.onAllNodes(alert, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(0)
        // A dot, not a number: no tab label gains a count.
        composeRule.onNodeWithText("Needs you").assertExists()
        composeRule.onAllNodes(SemanticsMatcher("has a digit") { node ->
            node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.any { text -> text.text.any(Char::isDigit) } == true
        }).assertCountEquals(0)
    }
}
