package com.orbit.app.ui.screens.home

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.R
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureInboxEvent
import com.orbit.app.domain.capture.needsImmediateTimeQuestion
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.ui.reminders.messageRes
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class CaptureProcessingState {
    Idle,
    Saving,
    Analyzing,
}

internal val CaptureProcessingState.isInProgress: Boolean
    get() = this != CaptureProcessingState.Idle

@StringRes
internal fun CaptureProcessingState.statusLabelRes(): Int? = when (this) {
    CaptureProcessingState.Idle -> null
    CaptureProcessingState.Saving -> R.string.core_home_saving_capture
    CaptureProcessingState.Analyzing -> R.string.core_home_analyzing_capture
}

/** A single quick question after saving a clearly time-sensitive thought. */
internal data class QuickReminderQuestion(
    val captureId: Long,
    val title: String,
    /** Null when LUMA could not tell the time and the user should pick one. */
    val reminderAt: Long?,
    val phrase: String?,
)

/** Messages Home can show; the text is resolved on screen so it follows the language. */
internal enum class HomeMessage(@param:StringRes val textRes: Int) {
    Saved(R.string.core_home_saved_let_go),
    SaveFailed(R.string.core_home_message_capture_save_failed),
    ReminderSet(ReminderSaveOutcome.Saved.messageRes()),
    ReminderNotScheduled(ReminderSaveOutcome.SavedNotScheduled.messageRes()),
    ReminderNotificationsOff(ReminderSaveOutcome.SavedNotificationsBlocked.messageRes()),
    ReminderFailed(R.string.core_home_message_capture_action_failed),
    KeptForLater(R.string.core_home_message_kept_in_inbox),
}

internal fun ReminderSaveOutcome.toHomeMessage(): HomeMessage = when (this) {
    ReminderSaveOutcome.Saved -> HomeMessage.ReminderSet
    ReminderSaveOutcome.SavedNotScheduled -> HomeMessage.ReminderNotScheduled
    ReminderSaveOutcome.SavedNotificationsBlocked -> HomeMessage.ReminderNotificationsOff
}

internal data class HomeCaptureUiState(
    val inputText: String = "",
    val processingState: CaptureProcessingState = CaptureProcessingState.Idle,
    /** Increments on every successful save; drives the send animation and haptic. */
    val savedPulse: Int = 0,
    val message: HomeMessage? = null,
    val quickReminder: QuickReminderQuestion? = null,
    val isSettingReminder: Boolean = false,
    /** A capture the sorting sheet should open (sort-right-after-saving or "Pick a time"). */
    val sortRequest: SortRequest? = null,
) {
    val isProcessing: Boolean
        get() = processingState.isInProgress
}

/** Starting a save clears earlier feedback; Home never waits for analysis. */
internal fun HomeCaptureUiState.beginCaptureSaving(): HomeCaptureUiState = copy(
    processingState = CaptureProcessingState.Saving,
    message = null,
    quickReminder = null,
)

internal data class SortRequest(val captureId: Long, val startWithReminderSetup: Boolean)

/**
 * Home only saves: type, send, saved. The thought is persisted before anything
 * else happens; LUMA analyses it afterwards and keeps the suggestion for Review.
 * Home asks one quick question only when a thought clearly needs a reminder.
 */
class HomeCaptureViewModel(
    private val captureInbox: CaptureInbox,
    private val appSettingsRepository: AppSettingsRepository,
    private val quickReminder: suspend (captureId: Long, title: String, reminderAt: Long) -> ReminderSaveOutcome,
    private val savedStateHandle: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    // The draft lives in the saved state so a half-typed thought survives Android
    // reclaiming LUMA in the background.
    private val _uiState = MutableStateFlow(
        HomeCaptureUiState(inputText = savedStateHandle.get<String>(DraftTextKey).orEmpty()),
    )
    internal val uiState: StateFlow<HomeCaptureUiState> = _uiState.asStateFlow()
    private var awaitingAnalysisFor: Long? = null
    /** True when the awaited thought was added for a Calendar day. */
    private var awaitingForCalendarDay = false

    init {
        viewModelScope.launch {
            captureInbox.events.collect { event ->
                if (event.captureId != awaitingAnalysisFor) return@collect
                awaitingAnalysisFor = null
                val forCalendarDay = awaitingForCalendarDay
                awaitingForCalendarDay = false
                when (event) {
                    is CaptureInboxEvent.Analyzed -> onAnalyzed(event.captureId, event.analysis, forCalendarDay)
                    // Added for a Calendar day: still open the sheet, so the day can be used.
                    is CaptureInboxEvent.AnalysisFailed -> if (forCalendarDay) {
                        _uiState.update { it.copy(sortRequest = SortRequest(event.captureId, startWithReminderSetup = false)) }
                    }
                }
            }
        }
    }

    /** Text shared from another app joins the draft; the user still decides to send it. */
    fun receiveSharedText(text: String) {
        onInputChanged(com.orbit.app.capture.SharedText.mergeIntoDraft(uiState.value.inputText, text))
    }

    fun onInputChanged(value: String) {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(inputText = value) }
        savedStateHandle[DraftTextKey] = value
    }

    /** Saves the thought. Returns false when there is nothing to save. */
    fun send(calendarDateContextEpochDay: Long? = null, spaceId: Long? = null): Boolean {
        val rawText = _uiState.value.inputText.trim()
        if (rawText.isBlank() || _uiState.value.isProcessing) return false
        val safeContext = calendarDateContextEpochDay
            ?.let { runCatching { LocalDate.ofEpochDay(it).toEpochDay() }.getOrNull() }
        _uiState.update(HomeCaptureUiState::beginCaptureSaving)
        viewModelScope.launch {
            try {
                // Registered before analysis starts, so a fast result is not missed.
                captureInbox.save(rawText, contextDateEpochDay = safeContext, spaceId = spaceId) { id ->
                    awaitingAnalysisFor = id
                    awaitingForCalendarDay = safeContext != null
                }
            } catch (_: Exception) {
                // Nothing was saved: keep the text in the box so it is not lost.
                _uiState.update {
                    it.copy(processingState = CaptureProcessingState.Idle, message = HomeMessage.SaveFailed)
                }
                return@launch
            }
            savedStateHandle[DraftTextKey] = ""
            _uiState.update {
                it.copy(
                    inputText = "",
                    processingState = CaptureProcessingState.Idle,
                    savedPulse = it.savedPulse + 1,
                    message = HomeMessage.Saved,
                )
            }
        }
        return true
    }

    private suspend fun onAnalyzed(captureId: Long, analysis: CaptureAnalysis, forCalendarDay: Boolean) {
        val settings = appSettingsRepository.settings.first()
        when (afterSaving(analysis, settings.sortRightAfterSaving, forCalendarDay, now())) {
            AfterSaving.OpenSortSheet ->
                _uiState.update { it.copy(sortRequest = SortRequest(captureId, startWithReminderSetup = false)) }
            AfterSaving.Nothing -> Unit
            AfterSaving.AskForTime -> _uiState.update {
                it.copy(
                    quickReminder = QuickReminderQuestion(
                        captureId = captureId,
                        title = analysis.suggestedTitle.ifBlank { analysis.rawText },
                        reminderAt = analysis.suggestedReminderAt?.takeIf { at -> at > now() },
                        phrase = analysis.reminderPhrase,
                    ),
                )
            }
        }
    }

    /** "Remind me" on the quick question: the user's tap is the confirmation. */
    fun confirmQuickReminder() {
        val question = _uiState.value.quickReminder ?: return
        val at = question.reminderAt
        if (at == null) {
            _uiState.update {
                it.copy(quickReminder = null, sortRequest = SortRequest(question.captureId, startWithReminderSetup = true))
            }
            return
        }
        if (_uiState.value.isSettingReminder) return
        _uiState.update { it.copy(isSettingReminder = true) }
        viewModelScope.launch {
            val outcome = runCatching { quickReminder(question.captureId, question.title, at) }
            _uiState.update {
                it.copy(
                    isSettingReminder = false,
                    quickReminder = null,
                    message = outcome.fold(
                        onSuccess = { it.toHomeMessage() },
                        onFailure = { HomeMessage.ReminderFailed },
                    ),
                )
            }
        }
    }

    /** Opens the full reminder setup to pick another time. */
    fun changeQuickReminderTime() {
        val question = _uiState.value.quickReminder ?: return
        _uiState.update {
            it.copy(quickReminder = null, sortRequest = SortRequest(question.captureId, startWithReminderSetup = true))
        }
    }

    /** "Not now": the thought stays in To sort. */
    fun dismissQuickReminder() {
        if (_uiState.value.quickReminder == null) return
        _uiState.update { it.copy(quickReminder = null, message = HomeMessage.KeptForLater) }
    }

    fun sortRequestHandled() {
        _uiState.update { it.copy(sortRequest = null) }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Clears [shown] only if it is still the current message, so a newer one is kept. */
    internal fun messageShown(shown: HomeMessage) {
        _uiState.update { if (it.message == shown) it.copy(message = null) else it }
    }

    class Factory(
        private val captureInbox: CaptureInbox,
        private val appSettingsRepository: AppSettingsRepository,
        private val quickReminder: suspend (captureId: Long, title: String, reminderAt: Long) -> ReminderSaveOutcome,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeCaptureViewModel::class.java))
            return HomeCaptureViewModel(
                captureInbox = captureInbox,
                appSettingsRepository = appSettingsRepository,
                quickReminder = quickReminder,
                savedStateHandle = savedStateHandle,
            ) as T
        }
    }

    private companion object {
        const val DraftTextKey = "homeCaptureDraft"
    }
}

internal enum class AfterSaving { OpenSortSheet, AskForTime, Nothing }

/**
 * What Home does once a saved thought is analysed. A thought added from a Calendar
 * day opens the sort sheet with that day preset, as if "Sort right after saving" were on.
 */
internal fun afterSaving(
    analysis: CaptureAnalysis,
    sortRightAfterSaving: Boolean,
    addedForCalendarDay: Boolean,
    now: Long,
): AfterSaving = when {
    sortRightAfterSaving || addedForCalendarDay -> AfterSaving.OpenSortSheet
    analysis.needsImmediateTimeQuestion(now) -> AfterSaving.AskForTime
    else -> AfterSaving.Nothing
}
