package com.orbit.app.ui.screens.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.mutableStateOf
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.time.OrbitTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainDumpSuggestionContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootCardKeepsSecondaryThoughtActionsInMoreMenu() {
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Task, spaceName = "Personal"),
                    state = interactionState(itemNumber = 2, totalItems = 6),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Suggestion 2 of 6").assertIsDisplayed()
        composeRule.onNodeWithText("Sort your thoughts").assertIsDisplayed()
        composeRule.onNodeWithText("Type: Task").assertIsDisplayed()
        composeRule.onNodeWithText("Space: Personal").assertIsDisplayed()
        // The item title and the primary "Set up task" action share this text.
        composeRule.onAllNodesWithText("Set up task").assertCountEquals(2)
        composeRule.onAllNodesWithText("Set up task")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Set up task")[1].assertIsDisplayed()
        composeRule.onNodeWithText("Edit details").assertIsDisplayed()
        composeRule.onNodeWithText("Finish later").assertIsDisplayed()
        composeRule.onAllNodesWithText("Keep this thought in Inbox").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("More Brain Dump actions").performClick()
        composeRule.onNodeWithText("Keep this thought in Inbox").assertIsDisplayed()
        composeRule.onNodeWithText("Skip this thought").assertIsDisplayed()
        composeRule.onNodeWithText("Discard remaining suggestions").assertIsDisplayed()
    }

    @Test
    fun whyThisAndEditorExposeCompleteTrustContext() {
        val suggestion = suggestion(type = SuggestedItemType.Task, spaceName = "Personal")
        val initial = interactionState(itemNumber = 1, totalItems = 1)
        // One composition; the stage changes through state, as it does in the app.
        val state = mutableStateOf(initial)
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion,
                    state = state.value,
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Why this?").performClick()
        composeRule.onNodeWithText("Original source thought").assertIsDisplayed()
        composeRule.onNodeWithText("This sounds actionable.").assertIsDisplayed()
        composeRule.onNodeWithText("Take the smallest next step").assertIsDisplayed()

        state.value = initial.copy(stage = BrainDumpStage.Edit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Original source thought").assertIsDisplayed()
        composeRule.onNodeWithText("This sounds actionable.").assertIsDisplayed()
    }

    @Test
    fun incompleteReminderSetupShowsRequiredTimeGuidanceAndCannotConfirm() {
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Reminder, spaceName = "Personal"),
                    state = interactionState(
                        itemNumber = 1,
                        totalItems = 1,
                        type = SuggestedItemType.Reminder,
                        scheduledAt = null,
                    ).copy(stage = BrainDumpStage.ReminderSetup),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Choose a reminder date and time first.").assertIsDisplayed()
        composeRule.onNodeWithText("Create reminder").assertIsNotEnabled()
    }

    @Test
    fun spacePickerKeepsLastOptionReachableWithManySpaces() {
        val spaces = listOf(CaptureSpaceOption(id = null, name = "Inbox")) +
            (1L..40L).map { id -> CaptureSpaceOption(id = id, name = "Space $id") }
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Note, spaceName = "Personal")
                        .copy(spaceOptions = spaces),
                    state = interactionState(itemNumber = 1, totalItems = 1).copy(stage = BrainDumpStage.Edit),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Space: Space 2").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Space 40"))
        composeRule.onNodeWithText("Space 40").assertIsDisplayed()
    }

    @Test
    fun persistentWarningRendersAlongsideLaterTransientStatus() {
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Note, spaceName = "Personal"),
                    state = interactionState(itemNumber = 1, totalItems = 1).copy(
                        status = BrainDumpStatus(
                            kind = BrainDumpStatusKind.PendingSkip,
                            message = BrainDumpStatusMessage.ThoughtSkipped,
                            canUndo = true,
                        ),
                        warning = BrainDumpStatus(
                            kind = BrainDumpStatusKind.Warning,
                            message = BrainDumpStatusMessage.NotificationAttention,
                        ),
                    ),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Reminder saved, but notification scheduling needs attention.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Skipped this thought").assertIsDisplayed()
    }

    @Test
    fun taskSetupSurfacesAndInvokesRetryForSaveFailure() {
        var retryInvocations = 0
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Task, spaceName = "Personal"),
                    state = interactionState(itemNumber = 2, totalItems = 6).copy(
                        stage = BrainDumpStage.TaskSetup,
                        status = BrainDumpStatus(
                            kind = BrainDumpStatusKind.Error,
                            message = BrainDumpStatusMessage.SaveFailed,
                            canRetry = true,
                        ),
                    ),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(onRetry = { retryInvocations += 1 }),
                )
            }
        }

        composeRule.onNodeWithText("That item did not save. The original dump is still in Inbox.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryInvocations)
        }
    }

    @Test
    fun changingItemRequestsFocusForTheNewProgressHeading() {
        val state = mutableStateOf(interactionState(itemId = "brain:1", itemNumber = 1, totalItems = 2))
        composeRule.setContent {
            OrbitTheme(settings = AppSettings()) {
                BrainDumpSuggestionContent(
                    suggestion = suggestionWithTwoItems(),
                    state = state.value,
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        state.value = interactionState(itemId = "brain:2", itemNumber = 2, totalItems = 2)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Suggestion 2 of 2").assertIsFocused()
    }

    private fun suggestion(type: SuggestedItemType, spaceName: String): CaptureSuggestion {
        val item = BrainDumpSuggestion(
            id = "item-2",
            rawText = "Original source thought",
            title = "Set up task",
            suggestedType = type,
            suggestedSpaceName = spaceName,
            confidence = 0.8f,
            tinyNextAction = "Take the smallest next step",
            reason = "This sounds actionable.",
        )
        return CaptureSuggestion(
            captureId = 7L,
            suggestedSpaceId = 2L,
            analysis = CaptureAnalysis(
                rawText = item.rawText,
                suggestedType = type,
                suggestedSpaceName = spaceName,
                suggestedNextAction = item.tinyNextAction,
                relatedTopics = emptyList(),
                reminderPossible = false,
                confidence = item.confidence,
                brainDumpItems = listOf(item),
            ),
            spaceOptions = listOf(
                CaptureSpaceOption(id = null, name = "Inbox"),
                CaptureSpaceOption(id = 2L, name = spaceName),
            ),
        )
    }

    private fun suggestionWithTwoItems(): CaptureSuggestion {
        val first = BrainDumpSuggestion(
            id = "brain:1",
            rawText = "First thought",
            title = "First thought",
            suggestedType = SuggestedItemType.Note,
            suggestedSpaceName = "Personal",
            confidence = 0.8f,
            tinyNextAction = "Keep the first thought",
            reason = "The thought is useful.",
        )
        val second = first.copy(
            id = "brain:2",
            rawText = "Second thought",
            title = "Second thought",
        )
        return suggestion(type = SuggestedItemType.Note, spaceName = "Personal").copy(
            analysis = CaptureAnalysis(
                rawText = first.rawText,
                suggestedType = first.suggestedType,
                suggestedSpaceName = first.suggestedSpaceName,
                suggestedNextAction = first.tinyNextAction,
                relatedTopics = emptyList(),
                reminderPossible = false,
                confidence = first.confidence,
                brainDumpItems = listOf(first, second),
            ),
        )
    }

    private fun interactionState(
        itemId: String = "item-2",
        itemNumber: Int,
        totalItems: Int,
        type: SuggestedItemType = SuggestedItemType.Task,
        scheduledAt: Long? = null,
    ) = BrainDumpInteractionState(
        stage = BrainDumpStage.Suggestion,
        itemId = itemId,
        itemNumber = itemNumber,
        totalItems = totalItems,
        initialDraft = BrainDumpDraft(
            title = "Set up task",
            type = type,
            spaceId = 2L,
            scheduledAt = scheduledAt,
        ),
        draft = BrainDumpDraft(
            title = "Set up task",
            type = type,
            spaceId = 2L,
            scheduledAt = scheduledAt,
        ),
        completionCounts = BrainDumpCompletionCounts(),
    )

    private fun recordingCallbacks(onRetry: () -> Unit = {}) = BrainDumpCallbacks(
        onPrimaryAction = {},
        onEdit = {},
        onDraftChanged = {},
        onContinueFromEditor = {},
        onStepBack = {},
        onKeepInInbox = {},
        onSkip = {},
        onUndoSkip = {},
        onRetry = onRetry,
        onFinishLater = {},
        onDiscardRemaining = {},
        onCloseCompletion = {},
    )
}
