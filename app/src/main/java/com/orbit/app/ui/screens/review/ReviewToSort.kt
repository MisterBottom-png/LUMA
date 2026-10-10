package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.capture.labelNames
import com.orbit.app.domain.capture.reminderStatus

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
    val brainDumpPending: Int = 0,
    val fromGemini: Boolean = false,
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
            )
            else -> {
                val reminderAt = suggestion.suggestedReminderAt?.takeIf { it > now }
                val needsChoice = suggestion.suggestedType == SuggestedItemType.Reminder &&
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
                    fromGemini = suggestion.analyzerSource == CaptureAnalyzerSource.Gemini.name,
                )
            }
        }
    }
