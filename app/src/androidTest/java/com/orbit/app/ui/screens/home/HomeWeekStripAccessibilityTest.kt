package com.orbit.app.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class HomeWeekStripAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun constrainedWidthAndLargeTextPreserveHeadingAndUnselectedDateSemantics() {
        val today = LocalDate.of(2026, 7, 14)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(220.dp)) {
                        WeekStrip(
                            uiState = HomeWeekUiState(
                                today = today,
                                visibleWeekDate = today,
                                datesWithItems = setOf(today),
                            ),
                            onDateSelected = {},
                            onVisibleWeekChanged = {},
                        )
                    }
                }
            }
        }

        composeRule.onNode(isHeading()).assertExists()
        composeRule.onNode(
            hasContentDescription("Today", substring = true),
        ).assertExists()
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false),
        ).assertCountEquals(7)
    }
}
