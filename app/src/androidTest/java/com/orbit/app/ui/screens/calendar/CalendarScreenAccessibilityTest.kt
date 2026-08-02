package com.orbit.app.ui.screens.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class CalendarScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun constrainedWidthAndLargeTextKeepCalendarLandmarksAndAddActionAvailable() {
        val today = LocalDate.of(2026, 7, 22)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(720.dp)) {
                        CalendarScreen(
                            uiState = CalendarUiState(
                                today = today,
                                selectedDate = today,
                                visibleMonth = YearMonth.from(today),
                                activeView = CalendarViewMode.Day,
                            ),
                            onBack = {},
                            onPreviousDay = {},
                            onNextDay = {},
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onToday = {},
                            onViewSelected = {},
                            onDateSelected = {},
                            timeFormat = OrbitTimeFormat(uses24HourClock = true),
                            onEntrySelected = {},
                            onAddForSelectedDate = {},
                        )
                    }
                }
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Calendar"),
        ).assertExists()
        composeRule.onNode(isHeading()).assertExists()
        composeRule.onNodeWithContentDescription("Add for this day").assertExists()
    }
}
