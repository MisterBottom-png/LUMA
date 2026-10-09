package com.orbit.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTutorialTest {
    @Test
    fun tutorialIsIncompleteUntilExplicitlyCompleted() {
        assertFalse(AppSettings().hasCompletedFirstTimeTutorial)
        assertTrue(
            AppSettings(hasCompletedFirstTimeTutorial = true)
                .hasCompletedFirstTimeTutorial,
        )
    }

    @Test
    fun resettingAppearancePreservesTutorialCompletion() {
        assertTrue(
            AppSettings(
                themeMode = SettingsThemeMode.Dark,
                hasCompletedFirstTimeTutorial = true,
            ).withDefaultAppearance().hasCompletedFirstTimeTutorial,
        )
    }
}
