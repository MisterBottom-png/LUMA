package com.orbit.app.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationVisibilityTest {
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
    }

    @Test
    fun appearanceIndex_keepsBottomNavigationVisible() {
        assertTrue(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = OrbitDestination.Settings.route,
                appearanceSubsectionOpen = false,
            ),
        )
    }

    @Test
    fun search_hidesBottomNavigationWithOrWithoutTheKeyboard() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = SearchDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = true,
                selectedRoute = SearchDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
    }

    @Test
    fun appearanceSubsection_hidesBottomNavigationUntilReturn() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = OrbitDestination.Settings.route,
                appearanceSubsectionOpen = true,
            ),
        )
        assertTrue(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = OrbitDestination.Settings.route,
                appearanceSubsectionOpen = false,
            ),
        )
    }

    @Test
    fun existingImeAndCalendarRulesRemainProtected() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = true,
                selectedRoute = OrbitDestination.Settings.route,
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
        assertTrue(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = OrbitDestination.Home.route,
                appearanceSubsectionOpen = false,
            ),
        )
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = CalendarDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
    }
}
