package com.orbit.app.ui.screens.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-receipt tip and the line before Android's notification question, as words on screen. */
@RunWith(AndroidJUnit4::class)
class FirstReceiptTipTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val politely = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun eachTipSaysWhatHappened() {
        var tip by mutableStateOf<FirstReceiptTip?>(FirstReceiptTip.Kept)
        composeRule.setContent { MaterialTheme { FirstReceiptTipLine(tip) } }

        composeRule.onNodeWithText("Tallele kept this. You’ll find it in To sort and in Search.").assert(politely)
        composeRule.runOnIdle { tip = FirstReceiptTip.Filed }
        composeRule.onNodeWithText("Tallele filed this for you. Tap Change if it got it wrong.").assert(politely)
        composeRule.runOnIdle { tip = FirstReceiptTip.Reminder }
        composeRule.onNodeWithText("The reminder is set. Tap Change to move it.").assert(politely)
        composeRule.runOnIdle { tip = null }
        composeRule.onNodeWithText("The reminder is set. Tap Change to move it.").assertDoesNotExist()
    }

    @Test
    fun theNotificationLineNamesTheReminderTimeIn24Hours() {
        val zone = ZoneId.systemDefault()
        val nineTomorrow = LocalDate.now(zone).plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val receipt = CaptureReceipt(
            captureId = 1L,
            outcome = CaptureOutcome.NeedsYou,
            reason = CaptureOutcomeReason.NotificationsOff,
            title = "Call the bank",
            itemType = ReceiptItemType.Reminder,
            itemId = 2L,
            reminderAt = nineTomorrow,
        )
        composeRule.setContent {
            MaterialTheme {
                NotificationPrimingLine(receipt, OrbitTimeFormat(uses24HourClock = true, locale = Locale.ENGLISH))
            }
        }

        composeRule.onNodeWithText("So this reminder can ring at 09:00, Tallele needs to show notifications.")
            .assert(politely)
    }
}
