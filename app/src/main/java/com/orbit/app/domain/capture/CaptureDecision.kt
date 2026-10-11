package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.analyzer.confidenceLevel

/**
 * What Tallele does with an analysed thought: file it ([Act]), ask one question
 * ([Ask]), or keep it quietly as a saved thought ([Keep]).
 */
sealed interface CaptureDecision {
    /** Clear enough to file as [type] without asking. */
    data class Act(val type: SuggestedItemType) : CaptureDecision

    /** One real question is needed; [reason] says which. */
    data class Ask(val reason: CaptureOutcomeReason) : CaptureDecision

    /** Nothing to do: the thought stays saved and findable, with no label and no badge. */
    data object Keep : CaptureDecision
}

/**
 * The act / ask / keep rule. Pure: no Android, no database, no clock of its own.
 *
 * - Only High confidence acts or asks, for every type; anything less is kept quietly.
 * - A High reminder is set only with a clear time that is still in the future ("in 1
 *   minute" counts). A missing, unclear or past time is one question; Tallele never
 *   creates a reminder for it.
 * - A note is filed only into a Space that was found; otherwise it is kept.
 * - A failed analysis or a multi-part Brain Dump is kept for the user to sort.
 *
 * @param hasSpace whether the thought has a real Space (chosen by the user or found by analysis).
 * @param now the current time in epoch milliseconds.
 */
fun decide(analysis: CaptureAnalysis, hasSpace: Boolean, now: Long): CaptureDecision {
    if (analysis.analyzerFailed || analysis.brainDumpItems.isNotEmpty()) return CaptureDecision.Keep
    val high = analysis.confidenceLevel == CaptureConfidence.High
    return when (analysis.suggestedType) {
        SuggestedItemType.Reminder -> when {
            // Not sure it is a reminder at all: keep it, no question.
            !high -> CaptureDecision.Keep
            analysis.reminderTimeStatus == ReminderTimeStatus.Resolved -> {
                val at = analysis.suggestedReminderAt ?: return CaptureDecision.Ask(CaptureOutcomeReason.TimeUnclear)
                if (at > now) {
                    CaptureDecision.Act(SuggestedItemType.Reminder)
                } else {
                    CaptureDecision.Ask(CaptureOutcomeReason.TimePassed)
                }
            }
            // An explicit reminder with no usable time: always ask.
            else -> CaptureDecision.Ask(CaptureOutcomeReason.TimeUnclear)
        }
        SuggestedItemType.Task -> if (high) CaptureDecision.Act(SuggestedItemType.Task) else CaptureDecision.Keep
        SuggestedItemType.Note -> if (high && hasSpace) CaptureDecision.Act(SuggestedItemType.Note) else CaptureDecision.Keep
        // A legacy storage token; never filed automatically.
        SuggestedItemType.MondayItem -> CaptureDecision.Keep
    }
}
