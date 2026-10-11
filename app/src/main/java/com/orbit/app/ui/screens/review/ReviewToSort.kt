package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.capture.isLowConfidence
import com.orbit.app.domain.capture.labelNames
import com.orbit.app.domain.capture.reminderStatus
import com.orbit.app.domain.capture.reminderTime
import com.orbit.app.domain.capture.taskDateEpochDay

enum class ToSortState {
    /** LUMA is still looking at a just-saved thought. */
    Analyzing,

    /** A suggestion is waiting for one-tap confirmation. */
    Suggested,

    /** The suggestion needs one choice first (for example a reminder time). */
    NeedsChoice,

    /** No suggestion (hidden by the user, or analysis failed): sort by hand. */
    NoSuggestion,

    /** A multi-part Brain Dump with pieces left to sort. */
    BrainDump,
}

data class ToSortItem(
    val captureId: Long,
    val text: String,
    val createdAt: Long,
    val state: ToSortState,
    val suggestedType: SuggestedItemType? = null,
    val suggestedTitle: String? = null,
    val suggestedSpaceName: String? = null,
    val suggestedLabels: List<String> = emptyList(),
    val reminderAt: Long? = null,
    /** For a task: its day, without a time. */
    val taskDateEpochDay: Long? = null,
    /** Tallele is not sure: no one tap, the user sorts it. */
    val lowConfidence: Boolean = false,
    val brainDumpPending: Int = 0,
    val fromGemini: Boolean = false,
    /** "Looks like N thoughts": a one-line thought the user may choose to split. */
    val possibleThoughts: Int = 0,
    /** The thought's stored outcome (Room v11), so screens can filter on it. */
    val outcome: CaptureOutcome = CaptureOutcome.Saved,
    val outcomeReason: CaptureOutcomeReason = CaptureOutcomeReason.None,
)

/** How long a fresh capture may show "LUMA is looking at it" before offering manual sorting. */
internal const val AnalysisGraceMillis: Long = 2L * 60_000L

/**
 * Every unresolved thought, newest first, with LUMA's stored suggestion. The list is
 * the same at every time of day; only the order of Review's sections changes.
 */
internal fun buildToSort(
    captures: List<CaptureEntity>,
    suggestions: Map<Long, CaptureSuggestionEntity>,
    brainDumpPending: Map<Long, Int>,
    brainDumpCaptureIds: Set<Long>,
    now: Long,
): List<ToSortItem> = captures
    .filter { it.status == CaptureStatus.Inbox }
    .sortedByDescending { it.createdAt }
    .map { capture ->
        val suggestion = suggestions[capture.id]
        when {
            capture.id in brainDumpCaptureIds -> ToSortItem(
                captureId = capture.id,
                text = capture.rawText,
                createdAt = capture.createdAt,
                state = ToSortState.BrainDump,
                brainDumpPending = brainDumpPending[capture.id] ?: 0,
                outcome = capture.outcome,
                outcomeReason = capture.outcomeReason,
            )
            suggestion == null || suggestion.dismissed -> ToSortItem(
                captureId = capture.id,
                text = capture.rawText,
                createdAt = capture.createdAt,
                state = if (suggestion == null && now - capture.createdAt < AnalysisGraceMillis) {
                    ToSortState.Analyzing
                } else {
                    ToSortState.NoSuggestion
                },
                outcome = capture.outcome,
                outcomeReason = capture.outcomeReason,
            )
            else -> {
                val reminderAt = suggestion.reminderTime()?.takeIf { it > now }
                val lowConfidence = suggestion.isLowConfidence
                val needsChoice = lowConfidence || suggestion.suggestedType == SuggestedItemType.Reminder &&
                    (reminderAt == null || suggestion.reminderStatus() != ReminderTimeStatus.Resolved)
                ToSortItem(
                    captureId = capture.id,
                    text = capture.rawText,
                    createdAt = capture.createdAt,
                    state = if (needsChoice) ToSortState.NeedsChoice else ToSortState.Suggested,
                    suggestedType = suggestion.suggestedType,
                    suggestedTitle = suggestion.suggestedTitle,
                    suggestedSpaceName = suggestion.suggestedSpaceName,
                    suggestedLabels = suggestion.labelNames(),
                    reminderAt = reminderAt,
                    taskDateEpochDay = suggestion.taskDateEpochDay(),
                    lowConfidence = lowConfidence,
                    fromGemini = suggestion.analyzerSource == CaptureAnalyzerSource.Gemini.name,
                    outcome = capture.outcome,
                    outcomeReason = capture.outcomeReason,
                )
            }
        }
    }

/** A thought that needs one answer from the user: its stored outcome is `NeedsYou`. */
data class NeedsYouItem(
    val captureId: Long,
    val text: String,
    val reason: CaptureOutcomeReason,
    val createdAt: Long,
    /** The reminder this thought became, when one exists (notifications off, not set, missed). */
    val reminderId: Long? = null,
    /** What a reminder or task made from the card is called: Tallele's title, else the thought. */
    val title: String = text,
    val suggestedSpaceName: String? = null,
    val taskDateEpochDay: Long? = null,
) {
    /** Something did not work (a reminder missed its time, or cannot ring): the tab may show a dot. */
    val somethingFailed: Boolean
        get() = reason == CaptureOutcomeReason.TimePassed || reason == CaptureOutcomeReason.NotificationsOff
}

/**
 * Needs you: every thought whose stored outcome is `NeedsYou`, newest first. A plain
 * saved or filed thought never appears here; it is browsable in Spaces and Search.
 */
internal fun buildNeedsYou(
    captures: List<CaptureEntity>,
    suggestions: Map<Long, CaptureSuggestionEntity>,
    reminders: List<ReminderEntity>,
): List<NeedsYouItem> {
    val remindersByCapture = reminders.filter { it.linkedCaptureId != null }.associateBy { it.linkedCaptureId }
    return captures
        .filter { it.outcome == CaptureOutcome.NeedsYou && it.status != CaptureStatus.Archived }
        .sortedByDescending { it.outcomeAt ?: it.updatedAt }
        .map { capture ->
            val suggestion = suggestions[capture.id]
            val reminder = remindersByCapture[capture.id]
                ?.takeIf { capture.status == CaptureStatus.Processed && it.id == capture.linkedItemId }
            NeedsYouItem(
                captureId = capture.id,
                text = capture.rawText,
                reason = capture.outcomeReason,
                createdAt = capture.createdAt,
                reminderId = reminder?.id,
                title = reminder?.title ?: suggestion?.suggestedTitle?.takeIf(String::isNotBlank) ?: capture.rawText,
                suggestedSpaceName = suggestion?.suggestedSpaceName,
                taskDateEpochDay = suggestion?.taskDateEpochDay(),
            )
        }
}

/** "Today Tallele kept N and filed M": thoughts saved today, by what happened to them. */
internal data class SavedTodayCounts(val kept: Int, val filed: Int)

internal fun savedTodayCounts(captures: List<CaptureEntity>, startOfToday: Long, startOfTomorrow: Long): SavedTodayCounts {
    val today = captures.filter { it.createdAt in startOfToday until startOfTomorrow && it.status != CaptureStatus.Archived }
    return SavedTodayCounts(
        kept = today.count { it.outcome == CaptureOutcome.Saved },
        filed = today.count { it.outcome == CaptureOutcome.Filed || it.outcome == CaptureOutcome.ReminderSet },
    )
}

/** The Needs you tab shows a dot only when something failed (a missed time, notifications off). */
internal fun needsYouSomethingFailed(captures: List<CaptureEntity>): Boolean = captures.any { capture ->
    capture.outcome == CaptureOutcome.NeedsYou &&
        capture.status != CaptureStatus.Archived &&
        (capture.outcomeReason == CaptureOutcomeReason.TimePassed || capture.outcomeReason == CaptureOutcomeReason.NotificationsOff)
}
