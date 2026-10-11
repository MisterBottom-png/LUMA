package com.orbit.app.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTouchHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.orbit.app.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    @Test
    fun todayButtonAndActionShowOnlyAwayFromTodaysWeek() {
        val today = LocalDate.of(2026, 7, 14)
        var state by mutableStateOf(HomeWeekUiState(today = today, visibleWeekDate = today))
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    WeekStrip(
                        uiState = state,
                        onDateSelected = {},
                        onVisibleWeekChanged = { offset ->
                            state = state.copy(visibleWeekDate = state.visibleWeekDate.plusWeeks(offset.toLong()))
                        },
                        onShowToday = { state = state.copy(visibleWeekDate = state.today) },
                    )
                }
            }
        }
        val todayButton = hasText(string(R.string.core_today)) and hasClickAction()
        val backToThisWeek = string(R.string.home_week_back_to_today)

        composeRule.onAllNodes(todayButton).assertCountEquals(0)
        composeRule.onAllNodes(hasCustomAction(backToThisWeek)).assertCountEquals(0)

        runCustomAction(string(R.string.core_home_show_next_week))

        composeRule.onNode(todayButton)
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
        composeRule.onAllNodes(hasCustomAction(backToThisWeek)).assertCountEquals(1)

        composeRule.onNode(todayButton).performClick()
        composeRule.waitForIdle()

        assertEquals(today, state.visibleWeekDate)
        composeRule.onAllNodes(todayButton).assertCountEquals(0)
        composeRule.onAllNodes(hasCustomAction(backToThisWeek)).assertCountEquals(0)
    }

    @Test
    fun backToThisWeekActionReturnsToToday() {
        val today = LocalDate.of(2026, 7, 14)
        var state by mutableStateOf(HomeWeekUiState(today = today, visibleWeekDate = today.plusWeeks(2)))
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    WeekStrip(
                        uiState = state,
                        onDateSelected = {},
                        onVisibleWeekChanged = {},
                        onShowToday = { state = state.copy(visibleWeekDate = state.today) },
                    )
                }
            }
        }

        runCustomAction(string(R.string.home_week_back_to_today))

        assertEquals(today, state.visibleWeekDate)
        composeRule.onAllNodes(hasCustomAction(string(R.string.home_week_back_to_today))).assertCountEquals(0)
    }

    @Test
    fun longPressOnADayAddsForThatDayAndIsLabelled() {
        val today = LocalDate.of(2026, 7, 14)
        var addedFor: LocalDate? = null
        var opened: LocalDate? = null
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    WeekStrip(
                        uiState = HomeWeekUiState(today = today, visibleWeekDate = today),
                        onDateSelected = { opened = it },
                        onVisibleWeekChanged = {},
                        onAddForDate = { addedFor = it },
                    )
                }
            }
        }
        val addForDay = string(R.string.home_week_add_for_day)

        composeRule.onAllNodes(
            SemanticsMatcher("long click labelled $addForDay") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == addForDay
            },
        ).assertCountEquals(7)

        composeRule.onAllNodes(hasContentDescription(string(R.string.core_today), substring = true))
            .onFirst()
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(today, addedFor)
        assertEquals(null, opened)
    }

    private fun runCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        composeRule.runOnUiThread {
            node.config[SemanticsActions.CustomActions].first { it.label == label }.action()
        }
        composeRule.waitForIdle()
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has custom action $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
