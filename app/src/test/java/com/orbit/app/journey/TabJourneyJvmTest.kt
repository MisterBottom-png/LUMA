package com.orbit.app.journey

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/**
 * The real app on the JVM, on a phone-sized screen: first launch, the four tabs,
 * and Settings from Home — the same journey the baseline profile records.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TabJourneyJvmTest {
    private val workManager = TestRule { base, _: Description ->
        object : Statement() {
            override fun evaluate() {
                WorkManagerTestInitHelper.initializeTestWorkManager(
                    ApplicationProvider.getApplicationContext<Context>(),
                    Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
                )
                base.evaluate()
            }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(workManager).around(compose)

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Taps the bottom-bar entry (the lowest node with that label) and checks the bar is still there. */
    private fun tapTab(label: String) {
        val nodes = compose.onAllNodesWithText(label)
        val bar = nodes.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.bottom }.index
        nodes[bar].performClick()
        compose.waitForIdle()
        listOf("Home", "Spaces", "Calendar", "Review").forEach { tab ->
            assertTrue("tab $tab missing after opening $label", count(tab) >= 1)
        }
    }

    @Test
    fun firstLaunch_tabsAndSettingsFromHome() {
        compose.waitUntil(10_000) { count("Skip") > 0 }
        compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()

        // A fresh install has no name yet: the greeting must not read "…, user".
        assertEquals(0, compose.onAllNodesWithText("user", substring = true).fetchSemanticsNodes().size)

        listOf("Spaces", "Calendar", "Review", "Home").forEach(::tapTab)
        compose.onNodeWithText("Ask Tallele").assertDoesNotExist()

        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Appearance", substring = true).assertExists()
        assertEquals("the tab bar is hidden in Settings", 0, count("Calendar"))
    }

    private fun onHome() = compose.onAllNodesWithContentDescription("Open settings").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun homeTabLeavesCalendarWhetherCalendarWasOpenedFromTheTabOrFromADay() {
        // The guide shows only on a first launch in this process.
        compose.waitUntil(10_000) { count("Skip") > 0 || onHome() }
        if (count("Skip") > 0) compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()

        // From the Calendar tab.
        tapTab("Calendar")
        tapTab("Home")
        assertTrue("Home tab from Calendar (opened from the tab)", onHome())

        // From a day on Home's week strip.
        val day = compose.onAllNodes(hasClickAction() and hasContentDescription("Today", substring = true))
        day[0].performClick()
        compose.waitForIdle()
        assertTrue("a day opens Calendar", !onHome())
        tapTab("Home")
        assertTrue("Home tab from Calendar (opened from a day)", onHome())

        // And again after visiting another tab in between.
        day[0].performClick()
        compose.waitForIdle()
        tapTab("Spaces")
        tapTab("Calendar")
        tapTab("Home")
        assertTrue("Home tab after Calendar, Spaces, Calendar", onHome())
    }

    @Test
    fun aTabKeepsItsPlaceWhenLeftThroughTheHomeTab() {
        compose.waitUntil(10_000) { count("Skip") > 0 || onHome() }
        if (count("Skip") > 0) compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()

        tapTab("Calendar")
        compose.onNodeWithContentDescription("Show the month").performClick()
        compose.waitForIdle()
        tapTab("Home")
        assertTrue("Home tab from Calendar", onHome())

        tapTab("Calendar")
        // Still the month view: its switch offers the day view.
        compose.onNodeWithContentDescription("Show the day").assertExists()
    }
}
