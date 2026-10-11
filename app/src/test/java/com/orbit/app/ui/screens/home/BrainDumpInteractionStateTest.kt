package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class BrainDumpInteractionStateTest {
    private val item = BrainDumpSuggestion(
        id = "brain:1",
        rawText = "Plan the appointment tomorrow at 1500",
        title = "Plan the appointment",
        suggestedType = SuggestedItemType.Task,
        suggestedSpaceName = "Personal",
        confidence = 0.9f,
        tinyNextAction = "Open the calendar",
        reason = "The thought is actionable.",
        suggestedReminderAt = 1_800_000_000_000L,
    )

    @Test
    fun initialDraftCarriesVisibleConfirmationMetadata() {
        val draft = initialBrainDumpDraft(
            item = item,
            spaces = listOf(
                CaptureSpaceOption(null, "Inbox"),
                CaptureSpaceOption(7L, "Personal"),
            ),
        )

        assertEquals("Plan the appointment", draft.title)
        assertEquals(SuggestedItemType.Task, draft.type)
        assertEquals(7L, draft.spaceId)
        // A task is date-only: the parsed time gives its day.
        assertEquals(null, draft.scheduledAt)
        assertEquals(
            java.time.Instant.ofEpochMilli(1_800_000_000_000L).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(),
            draft.scheduledDateEpochDay,
        )
    }

    @Test
    fun dirtyNestedDraftRequiresConfirmationButRootCloses() {
        val initial = BrainDumpDraft("Title", SuggestedItemType.Note, null, null)
        val changed = initial.copy(title = "Changed")

        assertEquals(
            BrainDumpDismissalDecision.ConfirmDiscard,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Edit,
                initialDraft = initial,
                draft = changed,
                actionInProgress = false,
            ),
        )
        assertEquals(
            BrainDumpDismissalDecision.CloseSession,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Suggestion,
                initialDraft = initial,
                draft = initial,
                actionInProgress = false,
            ),
        )
        // The card itself can be changed now, so leaving it with changes asks first.
        assertEquals(
            BrainDumpDismissalDecision.ConfirmDiscard,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Suggestion,
                initialDraft = initial,
                draft = changed,
                actionInProgress = false,
                openedFromOverview = true,
            ),
        )
    }

    @Test
    fun completionCountsSeparateSavedInboxAndSkipped() {
        val counts = BrainDumpCompletionCounts()
            .record(BrainDumpCompletedOutcome.Saved)
            .record(BrainDumpCompletedOutcome.Saved)
            .record(BrainDumpCompletedOutcome.KeptInInbox)
            .record(BrainDumpCompletedOutcome.Skipped)

        assertEquals(2, counts.saved)
        assertEquals(1, counts.keptInInbox)
        assertEquals(1, counts.skipped)
    }
}
