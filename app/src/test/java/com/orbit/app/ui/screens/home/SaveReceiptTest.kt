package com.orbit.app.ui.screens.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.R
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every receipt line, each with exactly one button, rendered on the JVM. */
@RunWith(AndroidJUnit4::class)
class SaveReceiptTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val timeFormat = OrbitTimeFormat(uses24HourClock = true, locale = Locale.ENGLISH)
    private val zone = ZoneId.systemDefault()
    private val tomorrow = LocalDate.now(zone).plusDays(1)
    private val tomorrowAtNine = tomorrow.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun receipt(
        outcome: CaptureOutcome,
        reason: CaptureOutcomeReason = CaptureOutcomeReason.None,
        decided: Boolean = true,
        spaceName: String? = null,
        itemType: ReceiptItemType? = null,
        taskDateEpochDay: Long? = null,
        reminderArmed: Boolean = false,
    ) = CaptureReceipt(
        captureId = 7L,
        outcome = outcome,
        reason = reason,
        decided = decided,
        title = "A thought",
        spaceName = spaceName,
        itemType = itemType,
        itemId = itemType?.let { 3L },
        taskDateEpochDay = taskDateEpochDay,
        reminderAt = tomorrowAtNine.takeIf { itemType == ReceiptItemType.Reminder },
        reminderArmed = reminderArmed,
    )

    private val cases: List<Triple<CaptureReceipt, String, ReceiptAction>> by lazy {
        listOf(
            Triple(receipt(CaptureOutcome.Saved, decided = false), "Saved.", ReceiptAction.View),
            Triple(receipt(CaptureOutcome.Saved), "Kept in To sort.", ReceiptAction.View),
            Triple(
                receipt(CaptureOutcome.Filed, spaceName = "Garden", itemType = ReceiptItemType.Note),
                "Filed in Garden as a note.",
                ReceiptAction.Change,
            ),
            Triple(
                receipt(CaptureOutcome.Filed, itemType = ReceiptItemType.Task, taskDateEpochDay = tomorrow.toEpochDay()),
                "Task for tomorrow.",
                ReceiptAction.Change,
            ),
            Triple(
                receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = true),
                "Reminder set for tomorrow 09:00.",
                ReceiptAction.Change,
            ),
            Triple(
                receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = false),
                context.getString(R.string.receipt_reminder_not_armed),
                ReceiptAction.TryAgain,
            ),
            Triple(
                receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimeUnclear),
                "Saved. When should this remind you?",
                ReceiptAction.ChooseTime,
            ),
            Triple(
                receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimePassed),
                "That time has already passed. Pick a new one?",
                ReceiptAction.ChooseTime,
            ),
            Triple(
                receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff, itemType = ReceiptItemType.Reminder),
                context.getString(R.string.receipt_notifications_off),
                ReceiptAction.TurnOn,
            ),
            Triple(
                receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled, itemType = ReceiptItemType.Reminder),
                context.getString(R.string.receipt_not_scheduled),
                ReceiptAction.TryAgain,
            ),
        )
    }

    @Test
    fun eachReceiptLineRendersWithItsOneButton() {
        var current by mutableStateOf(cases.first().first)
        val tapped = mutableListOf<ReceiptAction>()
        composeRule.setContent {
            MaterialTheme {
                SaveReceipt(
                    receipt = current,
                    timeFormat = timeFormat,
                    reduceMotion = true,
                    onAction = { _, action -> tapped += action },
                    onDismiss = {},
                )
            }
        }
        cases.forEach { (receipt, text, action) ->
            composeRule.runOnIdle { current = receipt }
            composeRule.onNodeWithText(text).assertExists()
            composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertCountEquals(1)
            composeRule.onNodeWithText(context.getString(action.labelRes)).performClick()
            assertEquals(text, action, tapped.last())
        }
    }

    @Test
    fun aReminderWithoutAnAlarmNeverSaysReminderSet() {
        val notArmed = receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = false)
        assertNotEquals(R.string.receipt_reminder_set, notArmed.line().textRes)
        assertEquals(R.string.receipt_reminder_not_armed, notArmed.line().textRes)
        assertEquals(ReceiptAction.TryAgain, notArmed.action())
    }

    @Test
    fun theReceiptCanBeHiddenWithoutSwiping() {
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                SaveReceipt(
                    receipt = receipt(CaptureOutcome.Saved),
                    timeFormat = timeFormat,
                    reduceMotion = true,
                    onAction = { _, _ -> },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithText("Kept in To sort.").assertExists()
        // TalkBack users get the swipe as a named action on the card.
        val hide = context.getString(R.string.core_hide)
        val node = composeRule.onNode(
            SemanticsMatcher("has the Hide action") { node ->
                node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }
                    ?.any { it.label == hide } == true
            },
        ).fetchSemanticsNode()
        composeRule.runOnIdle {
            node.config[SemanticsActions.CustomActions].first { it.label == hide }.action()
        }
        assertEquals(1, dismissed)
    }

    @Test
    fun receiptTimesNameTodayAndTomorrowInWordsAndOtherDaysByDate() {
        val today = LocalDate.of(2026, 10, 14)
        // The time of day is written in the phone's zone.
        val zone = ZoneId.systemDefault()
        fun at(day: LocalDate, h: Int) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val format = OrbitTimeFormat(uses24HourClock = true, locale = Locale.ENGLISH)
        assertEquals("today", formatReceiptTime(ReceiptTime.Day(today.toEpochDay()), today, zone, format, "Today", "Tomorrow"))
        assertEquals(
            "tomorrow",
            formatReceiptTime(ReceiptTime.Day(today.plusDays(1).toEpochDay()), today, zone, format, "Today", "Tomorrow"),
        )
        assertEquals(
            "homme",
            formatReceiptTime(ReceiptTime.Day(today.plusDays(1).toEpochDay()), today, zone, format, "Täna", "Homme"),
        )
        assertEquals(
            "tomorrow 09:00",
            formatReceiptTime(ReceiptTime.Moment(at(today.plusDays(1), 9)), today, zone, format, "Today", "Tomorrow"),
        )
        // Further away: the date, never a relative word.
        val later = formatReceiptTime(ReceiptTime.Day(today.plusDays(5).toEpochDay()), today, zone, format, "Today", "Tomorrow")
        assertEquals(format.formatDate(today.plusDays(5)), later)
    }
}
