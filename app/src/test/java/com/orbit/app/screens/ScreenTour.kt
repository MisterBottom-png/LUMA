package com.orbit.app.screens

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import com.orbit.app.ui.screens.home.homeWeekDates
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.longClick
import com.orbit.app.OrbitApplication
import com.orbit.app.data.local.StarterSpaces
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf
import java.io.File
import org.junit.Assert.assertTrue
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Design review tour: renders every main screen of the real app to PNG so the look can be
 * judged without a device. Runs only when LUMA_SCREENS_DIR is set (the "Screens" workflow);
 * ordinary unit-test runs skip it.
 *
 * Robolectric cannot draw Haze's blur shader, so the capture box, bottom bar and sheets show
 * no real blur in these PNGs; they are not the real glass look (check that on a phone).
 *
 * Each theme must run in its own test JVM: the app's database and settings store are
 * process-wide singletons, so a second tour in the same JVM starts with the first tour's
 * data (no first-run lines, duplicate Spaces). The Screens workflow runs them one by one.
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
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        drawOtherWindows(canvas, view)
        val dir = requireNotNull(outDir)
        dir.mkdirs()
        File(dir, "$theme-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Dialogs, menus and sheets live in their own windows, which drawing the activity
     * alone leaves out. Draw them on top, in the order they were added, with the dim a
     * dialog window asks for.
     */
    @Suppress("UNCHECKED_CAST")
    private fun drawOtherWindows(canvas: Canvas, activityRoot: View) {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
        views.filter { it !== activityRoot && it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 }
            .forEach { root ->
                val params = root.layoutParams as? WindowManager.LayoutParams
                if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                    canvas.drawColor(android.graphics.Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
                }
                val gravity = params?.gravity ?: Gravity.NO_GRAVITY
                val horizontal = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                val vertical = gravity and Gravity.VERTICAL_GRAVITY_MASK
                val x = when (horizontal) {
                    Gravity.CENTER_HORIZONTAL -> (activityRoot.width - root.width) / 2 + (params?.x ?: 0)
                    Gravity.RIGHT, Gravity.END -> activityRoot.width - root.width - (params?.x ?: 0)
                    else -> params?.x ?: 0
                }
                val y = when (vertical) {
                    Gravity.CENTER_VERTICAL -> (activityRoot.height - root.height) / 2 + (params?.y ?: 0)
                    Gravity.BOTTOM -> activityRoot.height - root.height - (params?.y ?: 0)
                    else -> params?.y ?: 0
                }
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                root.draw(canvas)
                canvas.restore()
            }
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

    /**
     * Step 6: Needs you empty, then with two questions (one time question, one missed time),
     * then with More open. Both questions are answered with "Just keep it" afterwards.
     */
    private fun needsYouScreens() {
        val container = (compose.activity.application as OrbitApplication).container
        tapTab("Needs you")
        step("needs you empty") { compose.waitUntil(10_000) { count("Nothing needs you right now.") > 0 } }
        shot("review-needs-you-empty")
        step("two questions") {
            runBlocking {
                val now = System.currentTimeMillis()
                container.captureRepository.insert(
                    CaptureEntity(
                        rawText = "Remind me about the car insurance",
                        outcome = CaptureOutcome.NeedsYou,
                        outcomeReason = CaptureOutcomeReason.TimeUnclear,
                        outcomeAt = now,
                        decidedAt = now,
                    ),
                )
                container.captureRepository.insert(
                    CaptureEntity(
                        rawText = "Call the plumber at 8:00",
                        outcome = CaptureOutcome.NeedsYou,
                        outcomeReason = CaptureOutcomeReason.TimePassed,
                        outcomeAt = now - 1,
                        decidedAt = now,
                    ),
                )
            }
            compose.waitUntil(10_000) { count("When should this remind you?") > 0 && count("This time has already passed.") > 0 }
        }
        shot("review-needs-you-two-cards")
        openNeedsYouMore()
        step("show the tools") {
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Look back at your week"))
        }
        shot("review-more-expanded")
        step("close more") {
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("More"))
            compose.onNodeWithText("More").performClick()
            compose.waitForIdle()
        }
        step("keep both") {
            repeat(2) { answered ->
                compose.onAllNodesWithText("Just keep it")[0].performClick()
                compose.waitUntil(10_000) { count("Just keep it") <= 1 - answered }
            }
            compose.waitUntil(10_000) { count("Nothing needs you right now.") > 0 }
        }
    }

    /** Step 9.6: a Space's "+" writes the thought over the Space, and the Space stays open. */
    private fun spaceCaptureScreens() {
        val container = (compose.activity.application as OrbitApplication).container
        step("space plus") {
            compose.onNodeWithContentDescription("Add a thought to Work").performClick()
            compose.waitUntil(10_000) { count("Add to Work") > 0 }
            compose.onNode(hasSetTextAction()).performTextInput("Draft the team update")
            compose.waitForIdle()
        }
        shot("space-capture-sheet")
        step("save in place") {
            compose.onNodeWithText("Save thought").performClick()
            compose.waitUntil(10_000) { count("Add to Work") == 0 }
            runBlocking {
                withTimeout(10_000) {
                    while (container.captureRepository.observeAll().first().none { it.rawText == "Draft the team update" }) delay(50)
                }
            }
            // Still on the Work Space, not sent back to Home.
            compose.onNodeWithContentDescription("Add a thought to Work").assertExists()
        }
        step("tidy the tour") {
            // Later shots expect the seeded thoughts only: put this one away again.
            runBlocking {
                container.captureRepository.observeAll().first()
                    .filter { it.rawText == "Draft the team update" }
                    .forEach { container.captureResolution.archive(it.id) }
            }
            compose.waitForIdle()
        }
    }

    /** Needs you keeps To sort, earlier tasks and the look back behind one "More" row. */
    private fun openNeedsYouMore() = step("needs you more") {
        val opened = count("Look back at your week") + count("Close the look back") > 0
        if (!opened) {
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("More"))
            compose.onNodeWithText("More").performClick()
            compose.waitForIdle()
        }
    }

    private fun capture(text: String) = step("capture $text") {
        compose.onNodeWithContentDescription("Capture text").performTextInput(text)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save thought").performClick()
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
            // A first launch adds the starter Spaces by itself; use those.
            val spaces = withTimeout(10_000) {
                while (container.spaceRepository.observeAll().first().size < StarterSpaces.templates.size) delay(50)
                container.spaceRepository.observeAll().first()
            }
            val ids = StarterSpaces.templates.take(5).associate { t ->
                t.key to spaces.first { it.name == t.storedName }.id
            }
            val tasks = container.taskRepository
            tasks.insert(TaskEntity(title = "Send the quarterly report", spaceId = ids["work"], dueAt = at(today, 11)))
            tasks.insert(TaskEntity(title = "Book a car service", spaceId = ids["personal"], dueAt = at(today.minusDays(2), 10)))
            tasks.insert(TaskEntity(title = "Fix the kitchen tap", spaceId = ids["home"]))
            tasks.insert(TaskEntity(title = "Prepare slides for Monday", spaceId = ids["work"], dueAt = at(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), 9)))
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
        if (prefix == "a") spaceCaptureScreens()
        back()
        tapTab("Calendar")
        shot("$prefix-calendar-day")
        step("month") { compose.onNodeWithContentDescription("Show the month").performClick(); compose.waitForIdle() }
        shot("$prefix-calendar-month")
        step("day") { compose.onNodeWithContentDescription("Show the day").performClick(); compose.waitForIdle() }
        tapTab("Needs you")
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
        step("look") { compose.onNodeWithText("Look").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-look")
        step("background") { compose.onNodeWithText("Background").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-background")
        back()
        step("text color") { compose.onNodeWithText("Text color").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-colors")
        back()
        step("transparency") { compose.onNodeWithText("Glass").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-transparency")
        back()
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
        step("type search") {
            compose.onNode(hasSetTextAction()).performTextInput("report")
            compose.waitUntil(10_000) { count("Send the quarterly report") > 0 }
        }
        shot("$prefix-search-results")
        back()
        back()
        tapTab("Needs you")
        openNeedsYouMore()
        step("find the thought") {
            // Scroll past To sort to the look-back row, so the thought sits clear of the bar.
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Look back at your week"))
        }
        step("review menu") { compose.onAllNodesWithText("Renew passport")[0].performTouchInput { longClick() }; compose.waitForIdle() }
        shot("$prefix-review-menu")
        step("change sheet") {
            compose.onNodeWithText("Change").performClick()
            compose.waitUntil(10_000) { count("Not now") > 0 }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        shot("$prefix-review-change")
        step("as a reminder") { compose.onNodeWithText("Reminder").performClick(); compose.waitForIdle() }
        shot("$prefix-review-change-choices")
        step("close sheet") {
            compose.onNodeWithText("Not now").performScrollTo().performClick()
            compose.waitUntil(10_000) { count("Not now") == 0 }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        step("one by one") {
            // Review orders its sections by time of day; after midday "To sort" comes
            // first and its header may have scrolled out of the list.
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Sort one by one"))
            compose.onNodeWithText("Sort one by one").performClick()
            compose.waitForIdle()
        }
        shot("$prefix-review-one-by-one")
        back()
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick(); compose.waitForIdle() }
        step("guide") { compose.onNodeWithText("First-time guide").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-guide-1")
        step("next") { compose.onNodeWithText("Continue").performClick(); compose.waitForIdle() }
        shot("$prefix-guide-2")
        step("next") { compose.onNodeWithText("Continue").performClick(); compose.waitForIdle() }
        shot("$prefix-guide-3")
        step("next") { compose.onNodeWithText("Continue").performClick(); compose.waitForIdle() }
        shot("$prefix-guide-4")
        step("leave guide") { compose.onNodeWithText("Skip").performClick(); compose.waitForIdle() }
        back()
        brainDump(prefix)
    }

    /**
     * Step 9 screens: Search before typing, from one letter and with no match; a Space made
     * from its name; a note's and a task's details; Ask leading with the question; plain
     * backup words. The voice notice is not shown here: Robolectric reports on-device
     * recognition, so the mic never opens the system screen (see VoiceCloudNoticeJvmTest).
     */
    private fun smallFixScreens(prefix: String) {
        tapTab("Spaces")
        step("search") {
            compose.onNodeWithContentDescription("Search").performClick()
            // The newest items come from the database off the main thread; wait for them.
            compose.waitUntil(10_000) { count("Recently saved") > 0 }
        }
        shot("$prefix-search-recent")
        step("one letter") {
            compose.onNode(hasSetTextAction()).performTextInput("g")
            compose.waitUntil(10_000) { count("2 results") > 0 }
        }
        shot("$prefix-search-one-letter")
        step("no match") {
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput("zzq")
            compose.waitUntil(10_000) { count("No matches for “zzq”.") > 0 }
        }
        shot("$prefix-search-no-match")
        back()
        back()
        tapTab("Spaces")
        step("new space by name") {
            compose.onNodeWithText("New Space").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).performTextInput("Garden")
            compose.waitForIdle()
        }
        shot("$prefix-space-create-name-only")
        step("colour and icon") { compose.onNodeWithText("Color and icon").performClick(); compose.waitForIdle() }
        shot("$prefix-space-create-style")
        step("cancel create") { compose.onNodeWithText("Cancel").performClick(); compose.waitForIdle() }
        step("open Personal") { compose.onAllNodesWithText("Personal")[0].performClick(); compose.waitForIdle() }
        step("open note") {
            compose.onAllNodesWithText("Gift ideas")[0].performClick()
            compose.waitUntil(10_000) { count("Space") > 0 }
        }
        shot("$prefix-item-note")
        back()
        back()
        step("open Work") { compose.onAllNodesWithText("Work")[0].performClick(); compose.waitForIdle() }
        step("open task") {
            compose.onAllNodesWithText("Send the quarterly report")[0].performClick()
            compose.waitUntil(10_000) { count("More") > 0 }
        }
        shot("$prefix-item-task")
        step("more details") { compose.onNodeWithText("More").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-item-task-more")
        back()
        back()
        tapTab("Needs you")
        step("ask") {
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Is anything actually urgent?").performClick()
            compose.waitUntil(10_000) { count("Local answer") > 0 }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        shot("$prefix-ask-answer-first")
        step("close ask") {
            compose.onNodeWithContentDescription("Close Situation AI").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick(); compose.waitForIdle() }
        step("backup") { compose.onNodeWithText("Backup and restore").performScrollTo().performClick(); compose.waitForIdle() }
        shot("$prefix-settings-backup")
        back()
        back()
    }

    /**
     * Steps 4 and 5: the save receipt under the capture box. Clear thoughts are filed by
     * Tallele itself (act); a reminder with no time asks one question in place of the
     * receipt (ask), and "Just keep it" ends it (keep). The reminder lines are shown first
     * with notifications off, then allowed.
     */
    private fun receiptScreens() {
        val app = compose.activity.application as OrbitApplication
        tapTab("Home")
        capture("Buy milk tomorrow")
        step("filed by itself") { compose.waitUntil(10_000) { count("Task for tomorrow.") > 0 } }
        shot("home-receipt-filed")
        step("notifications off") {
            shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        capture("Remind me tomorrow at 10:00 to water the roses")
        step("reminder, notifications off") { compose.waitUntil(10_000) { count("Turn on") > 0 } }
        // Step 7: the one line before Android asks for notifications (asked once, from Home).
        step("notification priming") {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("So this reminder can ring at", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        shot("home-receipt-needs-you")
        step("notifications on") {
            shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        capture("Remind me tomorrow at 9:00 to call the bank")
        step("reminder set by itself") {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Reminder set for", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        shot("home-receipt-reminder")
        capture("remind me about the car")
        step("one question") { compose.waitUntil(10_000) { count("Just keep it") > 0 } }
        shot("home-question-time")
        step("just keep it") {
            compose.onNodeWithText("Just keep it").performClick()
            compose.waitUntil(10_000) { count("Kept in To sort.") > 0 }
        }
        shot("home-question-kept")
    }

    /** Step 5: "Sort right after saving" sits under Settings › Reminders › Advanced. */
    private fun reminderAdvancedScreens() {
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick(); compose.waitForIdle() }
        step("advanced") {
            compose.onNodeWithText("Advanced").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Open the keyboard on Home").performScrollTo()
            compose.waitForIdle()
        }
        shot("settings-reminders-advanced")
        back()
    }

    /**
     * Step 2: the reminder health row in Settings, calm when nothing blocks reminders and
     * with one "Turn on" when notifications are off (red: a seeded reminder is due within a
     * day). The row re-reads Android on resume, so each change pauses and resumes the activity.
     */
    private fun reminderHealthScreens() {
        fun allowReminders(notifications: Boolean) {
            val app = compose.activity.application
            shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(notifications)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
        }
        tapTab("Home")
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick(); compose.waitForIdle() }
        step("reminders ok") {
            allowReminders(notifications = true)
            compose.waitUntil(10_000) { count("Nothing is blocking reminders.") > 0 }
        }
        shot("settings-reminders-ok")
        step("notifications off") {
            allowReminders(notifications = false)
            compose.waitUntil(10_000) { count("Notifications are off, so reminders can’t ring.") > 0 }
        }
        shot("settings-reminders-blocked")
        step("notifications back on") {
            allowReminders(notifications = true)
            compose.waitUntil(10_000) { count("Nothing is blocking reminders.") > 0 }
        }
        back()
    }

    /**
     * Home's week strip away from today: one week ahead with the "Today" button, then the
     * nearest week across two months ("Sep–Oct"). Each ends back on today's week.
     */
    private fun homeWeekScreens() {
        val todayButton = hasText("Today") and hasClickAction()
        fun backToToday(name: String) = step(name) {
            if (compose.onAllNodes(todayButton).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNode(todayButton).performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(todayButton).fetchSemanticsNodes().isEmpty() }
            }
        }
        tapTab("Home")
        step("next week") {
            weekAction("Show next week")
            compose.waitUntil(10_000) { compose.onAllNodes(todayButton).fetchSemanticsNodes().isNotEmpty() }
        }
        shot("home-week-other")
        backToToday("back from next week")
        step("split week") {
            val locale = compose.activity.resources.configuration.locales[0]
            val today = LocalDate.now(ZoneId.systemDefault())
            // A month start on the week's first day splits nothing, so look a few months ahead.
            val weeksAhead = (0..15).first { weeks ->
                val dates = homeWeekDates(today.plusWeeks(weeks.toLong()), locale)
                dates.first().month != dates.last().month
            }
            repeat(weeksAhead) { weekAction("Show next week") }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("–", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        shot("home-split-week")
        backToToday("back from split week")
    }

    /** Runs one of the week strip's TalkBack actions, as a swipe would. */
    private fun weekAction(label: String) {
        val node = compose.onNode(
            SemanticsMatcher("week action $label") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
            },
        ).fetchSemanticsNode()
        compose.runOnUiThread { node.config[SemanticsActions.CustomActions].first { it.label == label }.action() }
        compose.waitForIdle()
    }

    /** A messy list written in one go opens as a calm list of thoughts to tick and save. */
    private fun brainDump(prefix: String) {
        tapTab("Home")
        capture("Call the bank about the card\nBuy dog food\nShopping:\n- milk\n- eggs\nBook the dentist next week")
        tapTab("Needs you")
        openNeedsYouMore()
        step("find the dump") {
            compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Look back at your week"))
        }
        step("open dump") {
            compose.onAllNodesWithText("Call the bank", substring = true)[0].performScrollTo().performClick()
            compose.waitUntil(10_000) { count("Finish later") > 0 }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        shot("$prefix-brain-dump")
        step("open one") { compose.onAllNodesWithText("Buy dog food", substring = true).onLast().performClick(); compose.waitForIdle() }
        shot("$prefix-brain-dump-one")
        step("back to list") { compose.onNodeWithText("Back to the list").performScrollTo().performClick(); compose.waitForIdle() }
        step("finish later") {
            compose.onNodeWithText("Finish later").performScrollTo().performClick()
            compose.waitUntil(10_000) { count("Finish later") == 0 }
            compose.waitForIdle()
        }
        tapTab("Home")
    }

    @Test
    fun tour() {
        assumeTrue("set LUMA_SCREENS_DIR to render the screens", outDir != null)
        // Step 7: a first launch opens on Home with one line under the box; there is no guide to skip.
        step("first launch") { compose.waitUntil(30_000) { count(FirstRunLineLocal) > 0 } }
        shot("00-first-launch")
        seed()
        capture("Renew passport")
        step("first receipt tip") { compose.waitUntil(10_000) { count(FirstReceiptTipKept) > 0 } }
        shot("00-first-receipt-tip")
        listOf("Ideas for the garden", "Buy milk and coffee").forEach(::capture)
        shot("01-home")
        homeWeekScreens()
        needsYouScreens()

        mainScreens("a")
        extraScreens("x")
        smallFixScreens("s9")
        reminderHealthScreens()
        reminderAdvancedScreens()

        // The same screens in the violet look (accent + background).
        step("settings") { compose.onNodeWithContentDescription("Open settings").performClick() }
        step("look") { compose.onNodeWithText("Look").performScrollTo().performClick(); compose.waitForIdle() }
        step("violet accent") { compose.onNodeWithContentDescription("Tallele violet").performScrollTo().performClick() }
        step("background") { compose.onNodeWithText("Background").performScrollTo().performClick(); compose.waitForIdle() }
        step("violet mist") { compose.onAllNodesWithText("Violet Mist", substring = true)[0].performClick() }
        back()
        back()
        back()
        mainScreens("v")
        receiptScreens()

        outDir?.let { dir ->
            File(dir, "$theme-problems.txt").writeText(problems.joinToString("\n").ifEmpty { "none" })
        }
        // A step that failed means some renders show the wrong state; the run must say so.
        assertTrue(
            "Screen tour steps failed ($theme):\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }
}

private const val FirstRunLineLocal = "Saved only on this phone."
private const val FirstReceiptTipKept = "Tallele kept this. You’ll find it in To sort and in Search."

@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class ScreenTourLightTest : ScreenTour("light")

@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenTourDarkTest : ScreenTour("dark")
