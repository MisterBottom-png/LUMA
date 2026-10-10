package com.orbit.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationVisibilityTest {
    @Test
    fun barHoldsHomeSpacesCalendarReviewInThatOrder_andSettingsLivesOutsideIt() {
        assertEquals(
            listOf(
                OrbitDestination.Home,
                OrbitDestination.Spaces,
                OrbitDestination.Calendar,
                OrbitDestination.Review,
            ),
            OrbitDestination.bottomBar,
        )
        assertFalse(OrbitDestination.Settings.inBottomBar)
        assertEquals(CalendarDestination.BaseRoute, OrbitDestination.Calendar.navigationRoute)
        assertEquals(CalendarDestination.Route, OrbitDestination.Calendar.route)
    }

    @Test
    fun topLevelScreens_allowLiveGlassForTheFixedBottomNavigation() {
        assertTrue(shouldEnableHazeCapture(OrbitDestination.Home.route))
        assertTrue(shouldEnableHazeCapture(OrbitDestination.Spaces.route))
        assertTrue(shouldEnableHazeCapture(OrbitDestination.Review.route))
        assertTrue(shouldEnableHazeCapture(OrbitDestination.Settings.route))
    }

    @Test
    fun secondaryRoutes_keepTheStableGlassFallback() {
        assertFalse(shouldEnableHazeCapture(ItemDetailDestination.Route))
        assertFalse(shouldEnableHazeCapture(CalendarDestination.Route))
        assertFalse(shouldEnableHazeCapture(SearchDestination.Route))
        assertFalse(shouldEnableHazeCapture(FirstTimeTutorialDestination.Route))
    }

    @Test
    fun tabs_showTheBar_includingCalendar() {
        listOf(
            OrbitDestination.Home.route,
            OrbitDestination.Spaces.route,
            CalendarDestination.Route,
            OrbitDestination.Review.route,
        ).forEach { route ->
            assertTrue(
                route,
                shouldShowFloatingBottomNavigation(
                    imeVisible = false,
                    selectedRoute = route,
                    appearanceSubsectionOpen = false,
                ),
            )
        }
    }

    @Test
    fun settings_hidesTheBar() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = OrbitDestination.Settings.route,
                appearanceSubsectionOpen = false,
            ),
        )
    }

    @Test
    fun search_hidesBottomNavigationWithOrWithoutTheKeyboard() {
        listOf(false, true).forEach { imeVisible ->
            assertFalse(
                shouldShowFloatingBottomNavigation(
                    imeVisible = imeVisible,
                    selectedRoute = SearchDestination.Route,
                    appearanceSubsectionOpen = false,
                ),
            )
        }
    }

    @Test
    fun keyboardAndDetailScreens_hideTheBar() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = true,
                selectedRoute = OrbitDestination.Home.route,
                appearanceSubsectionOpen = false,
            ),
        )
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = ItemDetailDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = ReminderDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
    }
}
