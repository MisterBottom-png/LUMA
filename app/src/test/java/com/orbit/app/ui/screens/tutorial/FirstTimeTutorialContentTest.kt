package com.orbit.app.ui.screens.tutorial

import com.orbit.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstTimeTutorialContentTest {
    @Test
    fun tutorialHasFourShortStepsEndingWithPrivacy() {
        assertEquals(4, firstTimeTutorialPages.size)
        assertEquals(R.string.tutorial_write_title, firstTimeTutorialPages.first().titleRes)
        assertEquals(TutorialIllustration.Spaces, firstTimeTutorialPages[2].illustration)
        assertEquals(R.string.tutorial_private_title, firstTimeTutorialPages.last().titleRes)
        assertEquals(4, firstTimeTutorialPages.map { it.illustration }.distinct().size)
    }
}
