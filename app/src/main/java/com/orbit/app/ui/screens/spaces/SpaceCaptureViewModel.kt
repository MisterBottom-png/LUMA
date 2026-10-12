package com.orbit.app.ui.screens.spaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.capture.AutoFiler
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user asked the thought to become on the write step. */
enum class SpaceSaveAs { Decide, Note, Task, Reminder }

/** The steps of the Space "+" sheet (review §9.2). */
enum class SpaceCaptureStep {
    /** Write the thought and choose "Save as". */
    Write,

    /** Saved; Tallele is looking at it (usually a moment). */
    Looking,

    /** A reminder without a time: "When should this remind you?" */
    TimeQuestion,

    /** Day and time chips. */
    PickTime,

    /** Tallele isn't sure: "What should this be?" */
    TypeQuestion,
}

/** One quick time on the time question; the label is built from [at] and [day]. */
data class SpaceQuickTime(val at: Long, val day: QuickDay)

enum class QuickDay { Today, Tomorrow, Weekday }

data class SpaceCaptureUiState(
    /** The Space the sheet is open for; null when the sheet is closed. */
    val spaceId: Long? = null,
    val step: SpaceCaptureStep = SpaceCaptureStep.Write,
    val saveAs: SpaceSaveAs = SpaceSaveAs.Decide,
    /** The saved thought, once it exists. */
    val captureId: Long? = null,
    /** The thought's own words, shown on every step after saving. */
    val thought: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
    val quickTimes: List<SpaceQuickTime> = emptyList(),
    /** Set when the thought became something (or was kept); the screen reports it once. */
    val finished: SpaceCaptureFinished? = null,
)

/** How a sheet ended: [captureId]'s receipt says what it became; [keptForLater] when "Decide later" was chosen. */
data class SpaceCaptureFinished(val captureId: Long, val spaceId: Long, val keptForLater: Boolean = false)

/**
 * The Space "+" flow. The thought is saved into the Space the moment the user taps
 * "Save to …" (it shows under "Not sorted yet" until it is sorted), then the sheet asks
 * only for what is missing: a time for a reminder, or what it should be when Tallele isn't
 * sure. Drafts are kept per Space until they are saved or cleared.
 */
class SpaceCaptureViewModel(
    private val captureInbox: CaptureInbox,
    private val captureRepository: CaptureRepository,
    private val spaceRepository: SpaceRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val captureResolution: CaptureResolution,
    private val autoFiler: AutoFiler,
    private val savedStateHandle: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    /** How long to wait for analysis before asking the user directly. */
    private val lookingTimeoutMillis: Long = 8_000L,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceCaptureUiState())
    val uiState: StateFlow<SpaceCaptureUiState> = _uiState.asStateFlow()

    fun draft(spaceId: Long): String = savedStateHandle.get<String>(draftKey(spaceId)).orEmpty()

    /** The unsent draft for one Space; it survives closing the sheet and Android stopping the app. */
    fun draftFlow(spaceId: Long): StateFlow<String> = savedStateHandle.getStateFlow(draftKey(spaceId), "")

    fun onDraftChanged(spaceId: Long, text: String) {
        if (_uiState.value.busy) return
        savedStateHandle[draftKey(spaceId)] = text.take(com.orbit.app.capture.SharedText.MaxLength)
    }

    fun open(spaceId: Long) {
        _uiState.value = SpaceCaptureUiState(spaceId = spaceId)
    }

    /** "Sort" on a thought under "Not sorted yet": straight to "What should this be?". */
    fun openSort(spaceId: Long, captureId: Long) {
        viewModelScope.launch {
            val capture = captureRepository.getById(captureId)?.takeIf { it.status == CaptureStatus.Inbox } ?: return@launch
            _uiState.value = SpaceCaptureUiState(
                spaceId = spaceId,
                step = SpaceCaptureStep.TypeQuestion,
                captureId = captureId,
                thought = capture.rawText.trim(),
            )
        }
    }

    fun onSaveAsChanged(saveAs: SpaceSaveAs) {
        _uiState.update { it.copy(saveAs = saveAs) }
    }

    /** Closing never loses anything: the draft stays, and a saved thought stays in the Space. */
    fun dismiss() {
        if (_uiState.value.busy) return
        _uiState.value = SpaceCaptureUiState()
    }

    fun finishedShown() {
        _uiState.update { it.copy(finished = null) }
    }

    fun save() {
        val state = _uiState.value
        val spaceId = state.spaceId ?: return
        val text = draft(spaceId).trim()
        if (text.isBlank() || state.busy) return
        _uiState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val captureId = try {
                captureInbox.save(text, spaceId = spaceId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Nothing was saved: the text stays in the sheet and can be saved again.
                _uiState.update { it.copy(busy = false, failed = true) }
                return@launch
            }
            savedStateHandle[draftKey(spaceId)] = ""
            _uiState.update { it.copy(captureId = captureId, thought = text) }
            when (state.saveAs) {
                SpaceSaveAs.Note -> fileAsNote()
                SpaceSaveAs.Task -> fileAsTask()
                SpaceSaveAs.Reminder -> askForTime()
                SpaceSaveAs.Decide -> letTalleleDecide(captureId)
            }
        }
    }

    /** "What should this be?" answers. */
    fun chooseNote() = launchFiling { fileAsNote() }

    fun chooseTask() = launchFiling { fileAsTask() }

    fun chooseReminder() {
        if (_uiState.value.busy) return
        askForTime()
    }

    /** "Keep as a task, no reminder". */
    fun keepAsTask() = launchFiling { fileAsTask() }

    fun pickTime() {
        _uiState.update { it.copy(step = SpaceCaptureStep.PickTime) }
    }

    fun backToTimeQuestion() {
        _uiState.update { it.copy(step = SpaceCaptureStep.TimeQuestion) }
    }

    /** A future instant was chosen; a passed one is refused. */
    fun remindAt(epochMillis: Long) {
        if (epochMillis <= now()) {
            _uiState.update { it.copy(quickTimes = quickTimes()) }
            return
        }
        launchFiling {
            val captureId = requireNotNull(_uiState.value.captureId)
            captureResolution.createQuickReminder(captureId, suggestedTitle(captureId), epochMillis)
            finish(captureId)
        }
    }

    /** "Decide later": the thought stays in the Space under "Not sorted yet" and waits in Needs you. */
    fun decideLater() {
        val state = _uiState.value
        val captureId = state.captureId ?: return
        val spaceId = state.spaceId ?: return
        if (state.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { captureResolution.keep(captureId) }
            _uiState.value = SpaceCaptureUiState(finished = SpaceCaptureFinished(captureId, spaceId, keptForLater = true))
        }
    }

    private suspend fun letTalleleDecide(captureId: Long) {
        _uiState.update { it.copy(step = SpaceCaptureStep.Looking) }
        val deadline = now() + lookingTimeoutMillis
        while (captureInbox.sheetReadiness(captureId) == CaptureInbox.SheetReadiness.Waiting && now() < deadline) {
            delay(LookingPollMillis)
        }
        // AutoFiler may already have decided; this call does nothing then.
        runCatching { autoFiler.apply(captureId) }
        val capture = captureRepository.getById(captureId)
        when {
            capture == null -> _uiState.value = SpaceCaptureUiState()
            capture.status == CaptureStatus.Processed -> finish(captureId)
            capture.outcome == CaptureOutcome.NeedsYou &&
                (capture.outcomeReason == CaptureOutcomeReason.TimeUnclear || capture.outcomeReason == CaptureOutcomeReason.TimePassed) ->
                askForTime()
            else -> _uiState.update { it.copy(busy = false, step = SpaceCaptureStep.TypeQuestion) }
        }
    }

    private fun askForTime() {
        _uiState.update { it.copy(busy = false, step = SpaceCaptureStep.TimeQuestion, quickTimes = quickTimes()) }
    }

    private fun launchFiling(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                val captureId = _uiState.value.captureId
                val capture = captureId?.let { captureRepository.getById(it) }
                if (capture != null && capture.status == CaptureStatus.Processed) {
                    // Sorted meanwhile (for example by automatic filing): say what it became.
                    finish(capture.id)
                } else {
                    _uiState.update { it.copy(busy = false, failed = true) }
                }
            }
        }
    }

    private suspend fun fileAsNote() {
        val captureId = requireNotNull(_uiState.value.captureId)
        confirmCaptureAction.saveNote(captureId, activeSpaceId(), suggestedTitle(captureId))
        finish(captureId)
    }

    private suspend fun fileAsTask() {
        val captureId = requireNotNull(_uiState.value.captureId)
        confirmCaptureAction.createTask(captureId, activeSpaceId(), suggestedTitle(captureId), dueAt = null)
        finish(captureId)
    }

    private fun finish(captureId: Long) {
        val spaceId = _uiState.value.spaceId ?: return
        _uiState.value = SpaceCaptureUiState(finished = SpaceCaptureFinished(captureId, spaceId))
    }

    private suspend fun activeSpaceId(): Long? =
        _uiState.value.spaceId?.let { spaceRepository.getById(it) }?.takeUnless { it.archived }?.id

    private suspend fun suggestedTitle(captureId: Long): String =
        suggestionDao.getByCaptureId(captureId)?.suggestedTitle.orEmpty()

    /** Today 18:00 while it is at least 15 minutes ahead, tomorrow 9:00, and the coming Saturday 10:00. */
    internal fun quickTimes(): List<SpaceQuickTime> {
        val zone = zoneId()
        val current = now()
        val today = Instant.ofEpochMilli(current).atZone(zone).toLocalDate()
        val evening = today.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli()
        val morning = today.plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val saturdayDate = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val saturday = saturdayDate.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()
        return listOfNotNull(
            SpaceQuickTime(evening, QuickDay.Today).takeIf { evening - current >= MinimumLeadMillis },
            SpaceQuickTime(morning, QuickDay.Tomorrow),
            SpaceQuickTime(saturday, if (saturdayDate == today.plusDays(1)) QuickDay.Tomorrow else QuickDay.Weekday),
        )
    }

    /** Built with the saved state of whoever owns it, so drafts survive Android stopping the app. */
    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
            require(modelClass.isAssignableFrom(SpaceCaptureViewModel::class.java))
            return SpaceCaptureViewModel(
                captureInbox = container.captureInbox,
                captureRepository = container.captureRepository,
                spaceRepository = container.spaceRepository,
                suggestionDao = container.database.captureSuggestionDao(),
                confirmCaptureAction = container.confirmCaptureAction,
                captureResolution = container.captureResolution,
                autoFiler = container.autoFiler,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val LookingPollMillis = 150L
        const val MinimumLeadMillis = 15L * 60_000L

        fun draftKey(spaceId: Long) = "spaceCaptureDraft_$spaceId"
    }
}
