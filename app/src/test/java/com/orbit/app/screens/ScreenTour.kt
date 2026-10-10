package com.orbit.app.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import com.orbit.app.OrbitApplication
import com.orbit.app.data.local.StarterSpaces
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
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

    private fun back() = step("back") {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
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

    private fun scrollDown(name: String) = step("scroll $name") {
        compose.onAllNodes(hasScrollAction())
            .fetchSemanticsNodes()
            .withIndex()
            .maxByOrNull { it.value.boundsInRoot.height }
            ?.let { compose.onAllNodes(hasScrollAction())[it.index].performTouchInput { swipeUp(durationMillis = 400) } }
        compose.waitForIdle()
    }

    /** Realistic content, so Spaces, Calendar and Review are judged with data, not empty states. */
    private fun seed() = step("seed") {
        val container = (compose.activity.application as OrbitApplication).container
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            val ids = StarterSpaces.templates.take(5).mapIndexed { i, t ->
                t.key to container.spaceRepository.insert(StarterSpaces.spaceFor(t, sortOrder = i))
            }.toMap()
            val tasks = container.taskRepository
            tasks.insert(TaskEntity(title = "Send the quarterly report", spaceId = ids["work"], dueAt = at(today, 11)))
            tasks.insert(TaskEntity(title = "Book a car service", spaceId = ids["personal"], dueAt = at(today.minusDays(2), 10)))
            tasks.insert(TaskEntity(title = "Fix the kitchen tap", spaceId = ids["home"]))
            tasks.insert(TaskEntity(title = "Prepare slides for Monday", spaceId = ids["work"], dueAt = at(today.plusDays(3), 9)))
            tasks.insert(TaskEntity(title = "Pay the electricity bill", spaceId = ids["money"], dueAt = at(today.minusDays(1), 9)))
            tasks.insert(TaskEntity(title = "Stretch for ten minutes", spaceId = ids["health"], scheduledDateEpochDay = today.toEpochDay()))
            val reminders = container.reminderRepository
            reminders.insert(ReminderEntity(title = "Call the dentist", spaceId = ids["health"], dueAt = at(today, 9, 30)))
            reminders.insert(ReminderEntity(title = "Team stand-up", spaceId = ids["work"], dueAt = at(today, 14)))
            reminders.insert(ReminderEntity(title = "Pick up the parcel", spaceId = ids["home"], dueAt = at(today, 17, 30)))
            reminders.insert(ReminderEntity(title = "Water the plants", spaceId = ids["home"], dueAt = at(today.plusDays(1), 8)))
            val notes = container.noteRepository
            notes.insert(NoteEntity(title = "Gift ideas", body = "Book, scarf, concert tickets", spaceId = ids["personal"]))
            notes.insert(NoteEntity(title = "Garden plan", body = "Tulips by the fence", spaceId = ids["home"]))
        }
        compose.waitForIdle()
    }

    private fun mainScreens(prefix: String) {
        tapTab("Spaces")
        shot("$prefix-spaces")
        scrollDown("spaces")
        shot("$prefix-spaces-scrolled")
        step("open Work") { compose.onAllNodesWithText("Work")[0].performClick(); compose.waitForIdle() }
        shot("$prefix-space-detail")
        back()
        tapTab("Calendar")
        shot("$prefix-calendar-day")
        step("month") { compose.onNodeWithContentDescription("Show the month").performClick(); compose.waitForIdle() }
        shot("$prefix-calendar-month")
        step("day") { compose.onNodeWithContentDescription("Show the day").performClick(); compose.waitForIdle() }
        tapTab("Review")
        shot("$prefix-review")
        scrollDown("review")
        shot("$prefix-review-scrolled")
        scrollDown("review 2")
        shot("$prefix-review-scrolled2")
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick() }
        shot("$prefix-settings")
        scrollDown("settings")
        shot("$prefix-settings-scrolled")
        scrollDown("settings 2")
        shot("$prefix-settings-scrolled2")
        step("background") { compose.onNodeWithText("Background").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-background")
        back()
        back()
    }

    /** The smaller screens: guide, Space editor, menus, Search and the sort sheet. */
    private fun extraScreens(prefix: String) {
        tapTab("Spaces")
        step("new space") { compose.onNodeWithText("New Space").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-space-create")
        step("cancel create") { compose.onNodeWithText("Cancel").performClick(); compose.waitForIdle() }
        step("space menu") { compose.onAllNodesWithText("Work")[0].performTouchInput { longClick() }; compose.waitForIdle() }
        shot("$prefix-space-menu")
        step("edit space") { compose.onNodeWithText("Edit").performClick(); compose.waitForIdle() }
        shot("$prefix-space-edit")
        step("cancel edit") { compose.onNodeWithText("Cancel").performClick(); compose.waitForIdle() }
        step("search") { compose.onNodeWithContentDescription("Search").performClick(); compose.waitForIdle() }
        shot("$prefix-search")
        step("type search") { compose.onNode(hasSetTextAction()).performTextInput("the"); compose.waitForIdle() }
        shot("$prefix-search-results")
        back()
        back()
        tapTab("Review")
        step("review menu") { compose.onAllNodesWithText("Renew passport")[0].performTouchInput { longClick() }; compose.waitForIdle() }
        shot("$prefix-review-menu")
        step("change sheet") { compose.onNodeWithText("Change").performClick(); compose.waitForIdle() }
        shot("$prefix-review-change")
        step("more choices") { compose.onNodeWithText("Change action").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-review-change-choices")
        step("close sheet") { compose.onNodeWithText("Not now", substring = true).performScrollTo().performClick(); compose.waitForIdle() }
        step("one by one") { compose.onNodeWithText("Sort one by one").performClick(); compose.waitForIdle() }
        shot("$prefix-review-one-by-one")
        back()
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick(); compose.waitForIdle() }
        step("guide") { compose.onNodeWithText("First-time guide").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-guide-1")
        repeat(3) { step("next") { compose.onNodeWithText("Next").performClick(); compose.waitForIdle() } }
        shot("$prefix-guide-4")
        repeat(3) { step("next") { compose.onNodeWithText("Next").performClick(); compose.waitForIdle() } }
        shot("$prefix-guide-7")
        step("leave guide") { compose.onNodeWithText("Skip").performClick(); compose.waitForIdle() }
        back()
    }

    @Test
    fun tour() {
        assumeTrue("set LUMA_SCREENS_DIR to render the screens", outDir != null)
        step("first launch") { compose.waitUntil(30_000) { count("Skip") > 0 } }
        shot("00-tutorial")
        step("skip guide") { compose.onNodeWithText("Skip").performClick() }
        seed()
        listOf("Renew passport", "Ideas for the garden", "Buy milk and coffee").forEach(::capture)
        shot("01-home")

        mainScreens("a")
        extraScreens("x")

        // The same screens in the violet look (accent + background).
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick() }
        step("violet accent") { compose.onNodeWithContentDescription("Tallele violet").performScrollTo().performClick() }
        step("background") { compose.onNodeWithText("Background").performScrollTo().performClick(); compose.waitForIdle() }
        step("violet mist") { compose.onAllNodesWithText("Violet Mist", substring = true)[0].performClick() }
        back()
        back()
        mainScreens("v")

        outDir?.let { dir ->
            File(dir, "$theme-problems.txt").writeText(problems.joinToString("\n").ifEmpty { "none" })
        }
    }
}

@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class ScreenTourLightTest : ScreenTour("light")

@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenTourDarkTest : ScreenTour("dark")
