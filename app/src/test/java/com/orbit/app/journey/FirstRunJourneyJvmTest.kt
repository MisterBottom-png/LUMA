package com.orbit.app.journey

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import com.orbit.app.OrbitApplication
import com.orbit.app.data.local.StarterSpaces
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A new install, end to end: the app opens on Home at once with no guide and no dialog,
 * Home explains itself in two lines, the starter Spaces are there without a question,
 * and the first saved thought ends first run and shows the one-time tip.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FirstRunJourneyJvmTest {
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
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(workManager).around(compose)

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun onHome() = compose.onAllNodesWithContentDescription("Open settings").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aNewInstallOpensOnHomeLearnsFromTheFirstReceiptAndHasItsStarterSpaces() {
        val container = ApplicationProvider.getApplicationContext<OrbitApplication>().container
        // The settings store and database outlive one test in this JVM: start as a new install.
        runBlocking {
            container.appSettingsRepository.reset()
            container.spaceRepository.observeAll().first().forEach { container.spaceRepository.deleteById(it.id) }
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { onHome() }
            assertEquals("no guide to skip", 0, count("Skip"))
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            compose.onNodeWithContentDescription("Capture text").assertExists()
            assertEquals(1, count(LineAbove))
            assertEquals(1, count(LineBelow))

            // The starter Spaces were added without asking.
            awaitUntil { starterSpaceNames(container).size == StarterSpaces.templates.size }
            assertEquals(StarterSpaces.templates.map { it.storedName }.toSet(), starterSpaceNames(container))

            assertFalse(runBlocking { container.appSettingsRepository.settings.first().hasCompletedFirstTimeTutorial })
            compose.onNodeWithContentDescription("Capture text").performTextInput("parking code 4826")
            compose.onNodeWithContentDescription("Save thought").performClick()
            awaitUntil { runBlocking { container.appSettingsRepository.settings.first().hasCompletedFirstTimeTutorial } }
            awaitUntil { count(LineAbove) == 0 && count(LineBelow) == 0 }
            // The tip under the first receipt says what happened to this thought.
            awaitUntil { count(KeptTip) + count(FiledTip) == 1 }
            assertTrue(count("Skip") == 0)

            // Spaces shows them too, without having asked.
            val tabs = compose.onAllNodesWithText("Spaces")
            tabs[tabs.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.bottom }.index].performClick()
            awaitUntil { count("Personal") > 0 && count("Work") > 0 }
        }
    }

    /**
     * Waits for [condition] while letting the main thread run: the view model resumes there
     * after a save, and the test runs on that same thread.
     */
    private fun awaitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting")
            Thread.sleep(10)
        }
    }

    private fun starterSpaceNames(container: com.orbit.app.OrbitContainer): Set<String> = runBlocking {
        container.spaceRepository.observeAll().first().map { it.name }.toSet()
    }

    private companion object {
        const val LineAbove = "Write anything. Tallele keeps it safe and sorts what it can."
        const val LineBelow = "Everything stays on this phone unless you turn on Gemini or use voice."
        const val KeptTip = "Tallele kept this. You’ll find it in To sort and in Search."
        const val FiledTip = "Tallele filed this for you. Tap Change if it got it wrong."
    }
}
