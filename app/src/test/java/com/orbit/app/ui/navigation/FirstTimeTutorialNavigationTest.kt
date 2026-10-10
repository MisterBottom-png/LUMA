package com.orbit.app.ui.navigation

import com.orbit.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstTimeTutorialNavigationTest {
    @Test
    fun incompleteSettingsStartAtTutorial() {
        assertEquals(
            FirstTimeTutorialDestination.route(isReplay = false),
            initialOrbitRoute(AppSettings()),
        )
    }

    @Test
    fun completedSettingsStartAtHome() {
        assertEquals(
            OrbitDestination.Home.route,
            initialOrbitRoute(AppSettings(hasCompletedFirstTimeTutorial = true)),
        )
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
