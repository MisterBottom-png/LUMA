package com.orbit.app.ui.screens.home

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.R
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureInboxEvent
import com.orbit.app.domain.capture.needsImmediateTimeQuestion
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.reminders.ReminderSaveOutcome
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/**
 * Short flashes Home can still show; the text is resolved on screen so it follows the
 * language. Everything that went right is told by the save receipt, which is state;
 * these are only for something that went wrong.
 */
internal enum class HomeMessage(@param:StringRes val textRes: Int) {
    SaveFailed(R.string.core_home_message_capture_save_failed),
    ReminderFailed(R.string.core_home_message_capture_action_failed),
}

/** Which stored receipt Home may show right now. */
private sealed interface ReceiptGate {
    /** Whatever receipt is stored (for example after the app was reopened). */
    data object Open : ReceiptGate

    /** A new thought is being saved: the previous receipt is gone. */
    data object Saving : ReceiptGate

    /** Only the receipt of the thought just saved. */
    data class Expect(val captureId: Long) : ReceiptGate
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
 *
 * Under the capture box a save receipt says what became of the last thought. Its
 * thought's id is kept in the settings store and the text is read from the database,
 * so the receipt is the same after the app is killed and reopened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCaptureViewModel(
    private val captureInbox: CaptureInbox,
    private val appSettingsRepository: AppSettingsRepository,
    private val quickReminder: suspend (captureId: Long, title: String, reminderAt: Long) -> ReminderSaveOutcome,
    private val savedStateHandle: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
    /** The receipt of one thought, from the database (see [CaptureReceiptReader]). */
    private val receipts: (captureId: Long) -> Flow<CaptureReceipt?> = { flowOf(null) },
    /** Arms a saved reminder's alarm again and stores what that achieved ("Try again"). */
    private val retryReminder: suspend (captureId: Long, reminderId: Long) -> Unit = { _, _ -> },
) : ViewModel() {
    // The draft lives in the saved state so a half-typed thought survives Android
    // reclaiming LUMA in the background.
    private val _uiState = MutableStateFlow(
        HomeCaptureUiState(inputText = savedStateHandle.get<String>(DraftTextKey).orEmpty()),
    )
    internal val uiState: StateFlow<HomeCaptureUiState> = _uiState.asStateFlow()
    private var awaitingAnalysisFor: Long? = null
    private val receiptGate = MutableStateFlow<ReceiptGate>(ReceiptGate.Open)

    /** The save receipt under the capture box, or null when there is none to show. */
    val receipt: StateFlow<CaptureReceipt?> = combine(
        appSettingsRepository.settings
            .map { settings -> settings.lastReceiptCaptureId?.takeUnless { settings.lastReceiptDismissed } }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let(receipts) ?: flowOf(null) },
        receiptGate,
    ) { stored, gate ->
        when (gate) {
            ReceiptGate.Open -> stored
            ReceiptGate.Saving -> null
            is ReceiptGate.Expect -> stored?.takeIf { it.captureId == gate.captureId }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** True when the awaited thought was added for a Calendar day. */
    private var awaitingForCalendarDay = false

    /**
     * The one-time tip under the first receipt, matching what really happened to the
     * thought; null once it has been read or when the receipt is itself a question.
     */
    internal val firstReceiptTip: StateFlow<FirstReceiptTip?> = combine(
        receipt,
        appSettingsRepository.settings.map { it.hasSeenFirstReceiptTip }.distinctUntilChanged(),
    ) { current, seen ->
        current?.takeUnless { seen }?.let(::firstReceiptTipFor)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * A reminder from a thought saved just now that cannot ring until Android shows
     * notifications: Home says why in one line, then asks Android once. Never for a
     * receipt that was only restored on opening the app.
     */
    val notificationPriming: StateFlow<CaptureReceipt?> = combine(
        receipt,
        receiptGate,
        appSettingsRepository.settings.map { it.hasAskedForNotifications }.distinctUntilChanged(),
    ) { current, gate, asked ->
        current?.takeIf { !asked && gate is ReceiptGate.Expect && it.wantsNotifications() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        // A tip that was on screen has been read once the next thought is sent.
        val tipWasShown = firstReceiptTip.value != null
        _uiState.update(HomeCaptureUiState::beginCaptureSaving)
        receiptGate.value = ReceiptGate.Saving
        viewModelScope.launch {
            val id = try {
                // Registered before analysis starts, so a fast result is not missed.
                captureInbox.save(rawText, contextDateEpochDay = safeContext, spaceId = spaceId) { id ->
                    awaitingAnalysisFor = id
                    awaitingForCalendarDay = safeContext != null
                }
            } catch (_: Exception) {
                // Nothing was saved: keep the text in the box so it is not lost.
                receiptGate.value = ReceiptGate.Open
                _uiState.update {
                    it.copy(processingState = CaptureProcessingState.Idle, message = HomeMessage.SaveFailed)
                }
                return@launch
            }
            receiptGate.value = ReceiptGate.Expect(id)
            savedStateHandle[DraftTextKey] = ""
            _uiState.update {
                it.copy(
                    inputText = "",
                    processingState = CaptureProcessingState.Idle,
                    savedPulse = it.savedPulse + 1,
                )
            }
            // The thought is safe; now remember which one the receipt is about.
            storeReceipt {
                it.copy(lastReceiptCaptureId = id, lastReceiptDismissed = false)
                    .afterFirstSave(tipWasShown)
            }
        }
        return true
    }

    /** Swiped away, or its item opened: the receipt stays hidden until the next save. */
    fun dismissReceipt() {
        val captureId = receipt.value?.captureId ?: return
        val tipWasShown = firstReceiptTip.value != null
        viewModelScope.launch {
            storeReceipt { settings ->
                val dismissed = if (settings.lastReceiptCaptureId == captureId) {
                    settings.copy(lastReceiptDismissed = true)
                } else {
                    settings
                }
                if (tipWasShown) dismissed.copy(hasSeenFirstReceiptTip = true) else dismissed
            }
        }
    }

    /**
     * Android answered the notification request. Asked once, whatever the answer; when
     * allowed, a reminder that could not ring is armed again so the receipt turns into
     * "Reminder set".
     */
    fun notificationPermissionAnswered(granted: Boolean) {
        val primed = notificationPriming.value
        viewModelScope.launch {
            storeReceipt { it.copy(hasAskedForNotifications = true) }
        }
        if (granted && primed != null && primed.outcome == CaptureOutcome.NeedsYou) retryReceiptReminder()
    }

    /** "Choose time" for a thought that is still saved: the sort sheet opens on the reminder setup. */
    fun chooseReceiptTime() {
        val captureId = receipt.value?.captureId ?: return
        _uiState.update { it.copy(sortRequest = SortRequest(captureId, startWithReminderSetup = true)) }
    }

    /** "Try again" for a reminder whose alarm could not be set. */
    fun retryReceiptReminder() {
        val current = receipt.value ?: return
        val reminderId = current.reminderId ?: return
        if (_uiState.value.isSettingReminder) return
        _uiState.update { it.copy(isSettingReminder = true) }
        viewModelScope.launch {
            try {
                retryReminder(current.captureId, reminderId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(message = HomeMessage.ReminderFailed) }
            } finally {
                _uiState.update { it.copy(isSettingReminder = false) }
            }
        }
    }

    private suspend fun storeReceipt(change: (AppSettings) -> AppSettings) {
        try {
            appSettingsRepository.edit(change)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The thought itself is saved; only the receipt is lost.
        }
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
            // What the reminder achieved is stored on the thought; the receipt shows it.
            val outcome = runCatching { quickReminder(question.captureId, question.title, at) }
            _uiState.update {
                it.copy(
                    isSettingReminder = false,
                    quickReminder = null,
                    message = if (outcome.isFailure) HomeMessage.ReminderFailed else it.message,
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

    /** "Not now": the thought stays in To sort; the receipt under the box says it is saved. */
    fun dismissQuickReminder() {
        if (_uiState.value.quickReminder == null) return
        _uiState.update { it.copy(quickReminder = null) }
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
        private val receipts: (captureId: Long) -> Flow<CaptureReceipt?> = { flowOf(null) },
        private val retryReminder: suspend (captureId: Long, reminderId: Long) -> Unit = { _, _ -> },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeCaptureViewModel::class.java))
            return HomeCaptureViewModel(
                captureInbox = captureInbox,
                appSettingsRepository = appSettingsRepository,
                quickReminder = quickReminder,
                savedStateHandle = savedStateHandle,
                receipts = receipts,
                retryReminder = retryReminder,
            ) as T
        }
    }

    private companion object {
        const val DraftTextKey = "homeCaptureDraft"
    }
}

/**
 * After a thought is saved: Home's first-run lines are done, and a first-receipt tip
 * that was on screen counts as read.
 */
internal fun AppSettings.afterFirstSave(tipWasShown: Boolean): AppSettings = copy(
    hasCompletedFirstTimeTutorial = true,
    hasSeenFirstReceiptTip = hasSeenFirstReceiptTip || tipWasShown,
)

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
