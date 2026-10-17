package com.orbit.app.ui.components.parts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Limestone Harbour shared parts on the JVM, all at font scale 2 in a 320 dp wide box: key text
 * is shown, controls are at least 48 dp tall, state words are right, and text is not clipped.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class PartsJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val rule: ComposeContentTestRule get() = composeRule

    private fun polite() = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    // KeepButton

    @Test
    fun keepButton_callsOnClickOnceWithOneHapticTick_andNotWhileBusy() {
        var clicks = 0
        var busy by mutableStateOf(false)
        val haptics = CountingHaptics()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                PartsHost {
                    KeepButton(label = "Keep", onClick = { clicks++ }, busy = busy, busyDescription = "Keeping")
                }
            }
        }
        rule.onNodeWithText("Keep").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
        assertEquals(1, haptics.types.size)

        busy = true
        rule.waitForIdle()
        rule.onNodeWithText("Keep").performClick()
        assertEquals("busy blocks a double tap", 1, clicks)
        assertEquals("busy is silent", 1, haptics.types.size)

        busy = false
        rule.waitForIdle()
        rule.onNodeWithText("Keep").performClick()
        assertEquals(2, clicks)
        assertEquals(2, haptics.types.size)
    }

    @Test
    fun keepButton_disabledDoesNotClick_andLongLabelsWrapWithoutClipping() {
        var clicks = 0
        rule.setContent {
            PartsHost {
                KeepButton(label = "Pane tallele ja jätka hiljem", onClick = { clicks++ }, enabled = false)
            }
        }
        rule.onNodeWithText("Pane tallele ja jätka hiljem", useUnmergedTree = true).assertTextNotClipped()
        rule.onNodeWithText("Pane tallele ja jätka hiljem").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, clicks)
    }

    // CaptureBox

    @Test
    fun captureBox_showsPlaceholderThenTypedText_andKeepsActionsAtLeast48dp() {
        var kept = 0
        rule.setContent {
            PartsHost {
                var text by remember { mutableStateOf("") }
                CaptureBox(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "A thought, a plan",
                    label = "New thought",
                    trailing = { PartTextAction(label = "Voice", onClick = {}) },
                    actions = { KeepButton(label = "Keep", onClick = { kept++ }) },
                )
            }
        }
        rule.onNodeWithText("A thought, a plan", useUnmergedTree = true).assertExists()
        rule.onNode(hasSetTextAction()).performTextInput(RussianSample)
        rule.onNodeWithText(RussianSample).assertExists()
        rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onNodeWithText("Voice").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Keep").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, kept)
    }

    @Test
    fun captureBox_isLabelledForTalkBack_andGrowsWithFontScale() {
        var scale by mutableStateOf(1f)
        rule.setContent {
            PartsHost(fontScale = scale) {
                CaptureBox(value = "", onValueChange = {}, placeholder = "A thought", label = "New thought")
            }
        }
        rule.onNodeWithContentDescription("New thought").assertExists()
        val heightAt1x = rule.onNode(hasSetTextAction()).fetchSemanticsNode().size.height
        scale = 2f
        rule.waitForIdle()
        val heightAt2x = rule.onNode(hasSetTextAction()).fetchSemanticsNode().size.height
        assertTrue("2x ($heightAt2x) should be taller than 1x ($heightAt1x)", heightAt2x > heightAt1x)
    }

    // Receipt

    @Test
    fun receipt_keptShowsWordsAndActions_asPoliteLiveRegion() {
        var undone = 0
        rule.setContent {
            PartsHost {
                Receipt(
                    state = ReceiptState.Kept(
                        message = "Kept on this device.",
                        detail = EstonianSample,
                        actions = listOf(ReceiptAction("Open", {}), ReceiptAction("Undo", { undone++ })),
                    ),
                    thought = "Buy oat milk",
                )
            }
        }
        rule.onNodeWithText("Kept on this device.", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(EstonianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onNodeWithText("Buy oat milk").assertExists()
        rule.onAllNodes(polite()).assertCountEquals(1)
        rule.onNodeWithText("Open").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Undo").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, undone)
    }

    @Test
    fun receipt_failedShowsErrorWordsAndAnActionableRetry() {
        var retries = 0
        rule.setContent {
            PartsHost {
                Receipt(
                    ReceiptState.Failed(
                        message = "Couldn't keep that.",
                        retryLabel = "Try again",
                        onRetry = { retries++ },
                        detail = "Your text is still here.",
                    ),
                )
            }
        }
        rule.onNodeWithText("Couldn't keep that.", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Your text is still here.", useUnmergedTree = true).assertExists()
        rule.onAllNodes(polite()).assertCountEquals(1)
        rule.onNodeWithText("Try again")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun receipt_savingShowsTheOwnersWordAndNoActions() {
        rule.setContent { PartsHost { Receipt(ReceiptState.Saving("Keeping")) } }
        rule.onNodeWithText("Keeping", useUnmergedTree = true).assertExists()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
        rule.onAllNodes(polite()).assertCountEquals(1)
    }

    // DecisionCard

    @Test
    fun decisionCard_showsQuestionReasonAndChoices_eachAtLeast48dp() {
        val clicked = mutableListOf<String>()
        rule.setContent {
            PartsHost {
                DecisionCard(
                    title = "When should this remind you?",
                    reason = RussianSample,
                    thought = "Call the dentist",
                    choices = listOf(
                        DecisionChoice("Tomorrow 09:00") { clicked += "tomorrow" },
                        DecisionChoice("Pick a date and time") { clicked += "pick" },
                    ),
                    secondaryAction = DecisionChoice("Keep without a reminder") { clicked += "none" },
                )
            }
        }
        rule.onNodeWithText("When should this remind you?").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onNodeWithText("Call the dentist").assertExists()
        listOf("Tomorrow 09:00", "Pick a date and time", "Keep without a reminder").forEach {
            rule.onNodeWithText(it).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(listOf("tomorrow", "pick", "none"), clicked)
    }

    // ItemRow, FactRow, SectionHeader

    @Test
    fun itemRow_growsWithLongTextInAnyLanguage_andClickableRowIsOneButton() {
        var opened = 0
        rule.setContent {
            PartsHost {
                ItemRow(
                    title = RussianSample,
                    metadata = EstonianSample,
                    spaceColor = androidx.compose.ui.graphics.Color(0xFF5A7A4E),
                    onClick = { opened++ },
                )
            }
        }
        rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onNodeWithText(EstonianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onNode(hasClickAction())
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, opened)
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun itemRow_withoutOnClickIsPlainContent_andTrailingControlKeepsItsOwnFocus() {
        var trailing = 0
        rule.setContent {
            PartsHost {
                ItemRow(
                    title = "Buy oat milk",
                    trailing = { PartTextAction(label = "More", onClick = { trailing++ }) },
                )
            }
        }
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithText("More").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, trailing)
    }

    @Test
    fun itemRow_growsTallerAtLargeFontScale_insteadOfClipping() {
        var scale by mutableStateOf(1f)
        rule.setContent {
            PartsHost(fontScale = scale) {
                ItemRow(title = RussianSample, metadata = EstonianSample, onClick = {})
            }
        }
        val at1x = rule.onNode(hasClickAction()).fetchSemanticsNode().size.height
        scale = 2f
        rule.waitForIdle()
        val at2x = rule.onNode(hasClickAction()).fetchSemanticsNode().size.height
        assertTrue("2x row ($at2x) should be taller than 1x ($at1x)", at2x > at1x)
        rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
    }

    @Test
    fun theClippingCheckReallyFailsForClippedText() {
        rule.setContent {
            PartsHost {
                androidx.compose.material3.Text(
                    text = RussianSample,
                    maxLines = 1,
                    modifier = androidx.compose.ui.Modifier.width(80.dp),
                )
            }
        }
        val failed = try {
            rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
            false
        } catch (_: AssertionError) {
            true
        }
        assertTrue("a one-line, 80 dp wide label at 2x must be reported as clipped", failed)
    }

    @Test
    fun factRow_showsLabelAndValue_andChevronRowIsAtLeast48dp() {
        var opened = 0
        rule.setContent {
            PartsHost {
                FactRow(label = "Due", value = "Wed 14 Oct, 09:00", icon = Icons.Rounded.Event, onClick = { opened++ })
            }
        }
        rule.onNodeWithText("Due", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Wed 14 Oct, 09:00", useUnmergedTree = true).assertExists()
        rule.onNode(hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun sectionHeader_isAHeadingNotAllCaps_withAnActionAtLeast48dp() {
        var acted = 0
        rule.setContent {
            PartsHost {
                SectionHeader(title = "To decide", actionLabel = "Sort one by one", onAction = { acted++ })
            }
        }
        rule.onNodeWithText("To decide")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertTextEquals("To decide")
        rule.onNodeWithText("Sort one by one").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, acted)
    }

    // ChoiceSheet

    @Test
    fun choiceSheet_exposesSelectedState_asRadioButtons_atLeast48dp() {
        val picked = mutableListOf<String>()
        rule.setContent {
            PartsHost {
                ChoiceSheet(
                    title = "Remind me",
                    supportingText = EstonianSample,
                    options = listOf(
                        ChoiceOption("Tomorrow 09:00", description = "Wed 14 Oct", selected = true) { picked += "a" },
                        ChoiceOption("In an hour", selected = false) { picked += "b" },
                    ),
                )
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        rule.onAllNodes(radio).assertCountEquals(2)
        rule.onNodeWithText("Tomorrow 09:00").assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("In an hour").assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("b"), picked)
        rule.onNodeWithText("Remind me").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText(EstonianSample, useUnmergedTree = true).assertTextNotClipped()
    }

    // ReminderStatus

    @Test
    fun reminderStatus_showsDueNotifyAndRepeatAsThreeSeparateTargets() {
        val opened = mutableListOf<String>()
        rule.setContent {
            PartsHost {
                ReminderStatus(
                    delivery = ReminderDelivery.Scheduled("Reminder scheduled"),
                    dueLabel = "Due", dueValue = "Wed 14 Oct, 09:00",
                    notifyLabel = "Notify", notifyValue = "08:45 (15 minutes before)",
                    repeatLabel = "Repeat", repeatValue = "Does not repeat",
                    onDueClick = { opened += "due" },
                    onNotifyClick = { opened += "notify" },
                    onRepeatClick = { opened += "repeat" },
                )
            }
        }
        rule.onNodeWithText("Reminder scheduled", useUnmergedTree = true).assertExists()
        rule.onAllNodes(hasClickAction()).assertCountEquals(3)
        rule.onNodeWithText("Due").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Notify").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Repeat").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("due", "notify", "repeat"), opened)
        rule.onNodeWithText("08:45 (15 minutes before)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Wed 14 Oct, 09:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun reminderStatus_notScheduledAndProblemRenderTheStateTheyAreGiven() {
        var turnOn = 0
        var retry = 0
        var delivery: ReminderDelivery by mutableStateOf(
            ReminderDelivery.NotScheduled("Notifications are off", "Turn on") { turnOn++ },
        )
        rule.setContent {
            PartsHost {
                ReminderStatus(
                    delivery = delivery,
                    dueLabel = "Due", dueValue = "Wed",
                    notifyLabel = "Notify", notifyValue = "At the time",
                    repeatLabel = "Repeat", repeatValue = "Weekly",
                )
            }
        }
        rule.onNodeWithText("Notifications are off", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Reminder scheduled", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Turn on").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, turnOn)

        delivery = ReminderDelivery.Problem("The reminder could not be scheduled.", "Try again") { retry++ }
        rule.waitForIdle()
        rule.onNodeWithText("The reminder could not be scheduled.", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Try again").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retry)

        delivery = ReminderDelivery.NotScheduled("No time is set yet.")
        rule.waitForIdle()
        rule.onNodeWithText("No time is set yet.", useUnmergedTree = true).assertExists()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    // UndoMessage

    @Test
    fun undoMessage_showsMessageAndAnActionAtLeast48dp_asPoliteLiveRegion() {
        var undone = 0
        rule.setContent {
            PartsHost {
                UndoMessage(message = EstonianSample, actionLabel = "Undo", onAction = { undone++ })
            }
        }
        rule.onNodeWithText(EstonianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onAllNodes(polite()).assertCountEquals(1)
        rule.onNodeWithText("Undo").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, undone)
    }

    // DayAgenda

    @Test
    fun dayAgenda_groupsUntimedFirst_withTabularTimes_andOneTargetPerEntry() {
        val opened = mutableListOf<String>()
        rule.setContent {
            PartsHost {
                DayAgenda(
                    entries = listOf(
                        AgendaEntry(timeLabel = "09:00", title = "Call the dentist", metadata = "Notify 08:45") { opened += "call" },
                        AgendaEntry(title = RussianSample) { opened += "milk" },
                    ),
                    emptyText = "Nothing planned.",
                    anyTimeHeading = "Any time",
                    atTimeHeading = "At a time",
                )
            }
        }
        rule.onNodeWithText("Any time").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText("At a time").assertExists()
        rule.onNodeWithText("Nothing planned.").assertDoesNotExist()
        rule.onNodeWithText("09:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(RussianSample, useUnmergedTree = true).assertTextNotClipped()
        rule.onAllNodes(hasClickAction()).assertCountEquals(2)
        rule.onNodeWithText(RussianSample).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Call the dentist").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("milk", "call"), opened)
        val top = { text: String -> rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Any time comes before At a time", top("Any time") < top("At a time"))
    }

    @Test
    fun dayAgenda_emptyShowsTheOwnersText() {
        rule.setContent { PartsHost { DayAgenda(entries = emptyList(), emptyText = "Nothing planned.") } }
        rule.onNodeWithText("Nothing planned.").assertExists()
        rule.onAllNodesWithText("Any time").assertCountEquals(0)
    }

    @Test
    fun keepMark_isDecorativeUnlessDescribed() {
        rule.setContent {
            PartsHost {
                KeepMark()
                KeepMark(contentDescription = "Kept")
            }
        }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
        rule.onNodeWithContentDescription("Kept").assertExists()
    }
}
