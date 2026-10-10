package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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

        composeRule.onNodeWithText("Review").performClick()
        assertEquals(OrbitDestination.Review, selected)
    }
}
