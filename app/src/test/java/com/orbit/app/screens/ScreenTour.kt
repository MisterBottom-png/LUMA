package com.orbit.app.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design review tour: renders every main screen of the real app to PNG so the look can be
 * judged without a device. Runs only when LUMA_SCREENS_DIR is set (the "Screens" workflow);
 * ordinary unit-test runs skip it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenTour(private val theme: String) {
    private val outDir: File? = System.getenv("LUMA_SCREENS_DIR")?.let(::File)

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

    private val problems = mutableListOf<String>()

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { problems += "$name: ${it::class.simpleName}: ${it.message?.take(300)}" }
    }

    private fun shot(name: String) = step("shot $name") {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        // Draw the window into a bitmap (software canvas). PixelCopy-based
        // captureToImage never completes under Robolectric.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = requireNotNull(outDir)
        dir.mkdirs()
        File(dir, "$theme-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tapTab(label: String) = step("tab $label") {
        val nodes = compose.onAllNodesWithText(label)
        val bar = nodes.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.bottom }.index
        nodes[bar].performClick()
        compose.waitForIdle()
    }

    private fun capture(text: String) = step("capture $text") {
        compose.onNodeWithContentDescription("Capture text").performTextInput(text)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save and analyze capture").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    @Test
    fun tour() {
        assumeTrue("set LUMA_SCREENS_DIR to render the screens", outDir != null)
        step("first launch") { compose.waitUntil(30_000) { count("Skip") > 0 } }
        shot("00-tutorial")
        step("skip guide") { compose.onNodeWithText("Skip").performClick() }
        shot("01-home-empty")

        listOf("Renew passport", "Ideas for the garden", "Buy milk and coffee").forEach(::capture)
        shot("02-home-after-send")
        step("type") { compose.onNodeWithContentDescription("Capture text").performTextInput("Call the dentist") }
        shot("03-home-typing")

        tapTab("Spaces")
        shot("04-spaces")
        tapTab("Calendar")
        shot("05-calendar")
        tapTab("Review")
        shot("06-review")
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick() }
        shot("07-settings")
        step("appearance") { compose.onNodeWithText("Appearance", substring = true).performClick() }
        shot("08-appearance")

        outDir?.let { dir ->
            File(dir, "$theme-problems.txt").writeText(problems.joinToString("\n").ifEmpty { "none" })
        }
    }
}

@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class ScreenTourLightTest : ScreenTour("light")

@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenTourDarkTest : ScreenTour("dark")
