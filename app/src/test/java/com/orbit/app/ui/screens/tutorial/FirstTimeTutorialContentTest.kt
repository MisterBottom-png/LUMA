package com.orbit.app.ui.screens.tutorial

import com.orbit.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstTimeTutorialContentTest {
    @Test
    fun tutorialHasSevenOrderedPagesWithDistinctIllustrations() {
        assertEquals(7, firstTimeTutorialPages.size)
        assertEquals(R.string.tutorial_idea_title, firstTimeTutorialPages.first().titleRes)
        assertEquals(R.string.tutorial_control_title, firstTimeTutorialPages.last().titleRes)
        assertEquals(7, firstTimeTutorialPages.map { it.illustration }.distinct().size)
    }
}
