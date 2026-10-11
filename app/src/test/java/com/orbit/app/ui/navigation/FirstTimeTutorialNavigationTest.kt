package com.orbit.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstTimeTutorialNavigationTest {
    @Test
    fun everyLaunchStartsAtHomeAndTheGuideIsOnlyAReplay() {
        // A new user learns the app from Home and the first receipt, not from the guide.
        assertEquals(OrbitDestination.Home.route, initialOrbitRoute())
        assertEquals("tutorial?replay=true", FirstTimeTutorialDestination.route(isReplay = true))
    }

    @Test
    fun tutorialNeverShowsBottomNavigation() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = FirstTimeTutorialDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
    }
}
