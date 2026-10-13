package com.orbit.app.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.capture.labelNames
import com.orbit.app.domain.capture.taskStatus
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.ReminderTimeCheck
import com.orbit.app.ui.screens.spaces.SpaceQuickTime
import com.orbit.app.ui.screens.spaces.quickReminderTimes
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of the Needs you one-item sheet (review §9.3). */
enum class SortOneStep {
    /** Tallele has a suggestion: "Save it like this?" */
    Confirm,

    /** Tallele isn't sure: Space chips and "What should this be?" */
    Type,

    /** A reminder without a time: "When should this remind you?" */
    Time,

    /** Day and time chips. */
    PickTime,
}

data class SortOneUiState(
    /** The thought being sorted; null when the sheet is closed. */
    val captureId: Long? = null,
    val step: SortOneStep = SortOneStep.Confirm,
    /** The thought's words, editable; they become the item's title. */
    val title: String = "",
    val createdAt: Long = 0L,
    val suggestedType: SuggestedItemType? = null,
    /** The chosen Space; null is "No Space". */
    val spaceId: Long? = null,
    val taskDateEpochDay: Long? = null,
    val reminderAt: Long? = null,
    /** Someday or Waiting for, shown on the suggestion and kept when filed (F08). */
    val taskStatus: TaskStatus = TaskStatus.Open,
    /** The type chosen on the Type step, shown above the time question. */
    val chosenType: SuggestedItemType? = null,
    val quickTimes: List<SpaceQuickTime> = emptyList(),
    val busy: Boolean = false,
    val failed: Boolean = false,
    /** Set once the thought was sorted or kept; the screen reports it once. */
    val finished: ReviewSortMessage? = null,
)

/**
 * Sorts one thought from Needs you without the full sort sheet. Nothing is filed until
 * the user taps a choice; "Not now" closes the sheet and changes nothing; "Decide
 * later" keeps the thought in To sort as the user's decision.
 */
class SortOneViewModel(
    private val captureRepository: CaptureRepository,
    private val spaceRepository: SpaceRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val captureResolution: CaptureResolution,
    private val now: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SortOneUiState())
    val uiState: StateFlow<SortOneUiState> = _uiState.asStateFlow()

    fun open(item: ToSortItem) {
        viewModelScope.launch {
            val capture = captureRepository.getById(item.captureId)?.takeIf { it.status == CaptureStatus.Inbox }
                ?: return@launch
            val spaceId = item.suggestedSpaceName?.let { name ->
                spaceRepository.observeAllOnce().firstOrNull { !it.archived && it.name.equals(name, ignoreCase = true) }?.id
            } ?: capture.suggestedSpaceId
            val reminderWithoutTime = item.suggestedType == SuggestedItemType.Reminder && item.reminderAt == null
            _uiState.value = SortOneUiState(
                captureId = capture.id,
                step = when {
                    item.state == ToSortState.Suggested -> SortOneStep.Confirm
                    reminderWithoutTime && !item.lowConfidence -> SortOneStep.Time
                    else -> SortOneStep.Type
                },
                title = (item.suggestedTitle?.takeIf { it.isNotBlank() } ?: capture.rawText).trim(),
                createdAt = capture.createdAt,
                suggestedType = item.suggestedType,
                spaceId = spaceId,
                taskDateEpochDay = item.taskDateEpochDay,
                reminderAt = item.reminderAt,
                taskStatus = suggestionDao.getByCaptureId(capture.id)?.taskStatus() ?: TaskStatus.Open,
                chosenType = if (reminderWithoutTime) SuggestedItemType.Reminder else null,
                quickTimes = quickReminderTimes(now(), zoneId()),
            )
        }
    }

    fun onTitleChange(text: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(title = text.take(com.orbit.app.capture.SharedText.MaxLength)) }
    }

    fun selectSpace(spaceId: Long?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(spaceId = spaceId) }
    }

    /** "Save as …" on the suggestion. */
    fun saveSuggested() {
        when (_uiState.value.suggestedType) {
            SuggestedItemType.Note -> chooseNote()
            SuggestedItemType.Task, SuggestedItemType.MondayItem -> fileTask(withSuggestedDate = true)
            SuggestedItemType.Reminder -> _uiState.value.reminderAt?.let(::remindAt) ?: chooseReminder()
            null -> chooseSomethingElse()
        }
    }

    fun chooseSomethingElse() {
        _uiState.update { it.copy(step = SortOneStep.Type, failed = false) }
    }

    fun chooseNote() = file(ReviewSortMessage.NoteSaved) { state, captureId ->
        confirmCaptureAction.saveNote(
            captureId = captureId,
            spaceId = activeSpaceId(state.spaceId),
            title = state.title,
            labelNames = suggestionDao.getByCaptureId(captureId)?.labelNames().orEmpty(),
        )
    }

    fun chooseTask() = fileTask(withSuggestedDate = false)

    fun chooseReminder() {
        if (_uiState.value.busy) return
        _uiState.update {
            it.copy(
                step = SortOneStep.Time,
                chosenType = SuggestedItemType.Reminder,
                quickTimes = quickReminderTimes(now(), zoneId()),
                failed = false,
            )
        }
    }

    /** "Keep as a task, no reminder". */
    fun keepAsTask() = fileTask(withSuggestedDate = false)

    fun pickTime() {
        _uiState.update { it.copy(step = SortOneStep.PickTime) }
    }

    fun backToTime() {
        _uiState.update { it.copy(step = SortOneStep.Time) }
    }

    /** Back from the time question to "What should this be?". */
    fun backFromTime() {
        _uiState.update { it.copy(step = SortOneStep.Type) }
    }

    /** A future instant was chosen; a passed one is refused and the quick times are renewed. */
    fun remindAt(epochMillis: Long) {
        if (ReminderTimeCheck.problem(epochMillis, 0L, now()) != null) {
            _uiState.update { it.copy(step = SortOneStep.Time, quickTimes = quickReminderTimes(now(), zoneId())) }
            return
        }
        file(null) { state, captureId ->
            val outcome = captureResolution.createReminderAs(captureId, state.title, state.spaceId, epochMillis)
            when (outcome) {
                ReminderSaveOutcome.Saved -> ReviewSortMessage.ReminderSet
                ReminderSaveOutcome.SavedNotificationsBlocked -> ReviewSortMessage.ReminderNotificationsOff
                else -> ReviewSortMessage.ReminderNotScheduled
            }
        }
    }

    /** "Decide later": stays in To sort, as the user's decision. */
    fun decideLater() = file(ReviewSortMessage.Kept) { _, captureId -> captureResolution.keep(captureId) }

    /** "Not now" or closing the sheet: nothing changes. */
    fun dismiss() {
        if (_uiState.value.busy) return
        _uiState.value = SortOneUiState()
    }

    fun finishedShown() {
        _uiState.update { it.copy(finished = null) }
    }

    private fun fileTask(withSuggestedDate: Boolean) = file(ReviewSortMessage.TaskMade) { state, captureId ->
        val suggestion = suggestionDao.getByCaptureId(captureId)
        confirmCaptureAction.createTask(
            captureId = captureId,
            spaceId = activeSpaceId(state.spaceId),
            title = state.title,
            dueAt = null,
            scheduledDateEpochDay = state.taskDateEpochDay.takeIf { withSuggestedDate },
            status = suggestion?.taskStatus() ?: TaskStatus.Open,
            labelNames = suggestion?.labelNames().orEmpty(),
        )
    }

    /**
     * Runs one filing action. [done] is the message, or the action returns its own
     * (a [ReviewSortMessage]) when [done] is null.
     */
    private fun file(done: ReviewSortMessage?, action: suspend (SortOneUiState, Long) -> Any?) {
        val state = _uiState.value
        val captureId = state.captureId ?: return
        if (state.busy || state.title.isBlank()) return
        _uiState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                val result = action(state.copy(title = state.title.trim()), captureId)
                val message = done ?: (result as? ReviewSortMessage) ?: ReviewSortMessage.Filed
                _uiState.value = SortOneUiState(finished = message)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                val sortedMeanwhile = captureRepository.getById(captureId)?.status != CaptureStatus.Inbox
                _uiState.value = if (sortedMeanwhile) {
                    SortOneUiState(finished = ReviewSortMessage.Filed)
                } else {
                    _uiState.value.copy(busy = false, failed = true)
                }
            }
        }
    }

    private suspend fun activeSpaceId(id: Long?): Long? =
        id?.let { spaceRepository.getById(it) }?.takeUnless { it.archived }?.id

    private suspend fun SpaceRepository.observeAllOnce() = observeAll().first()

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SortOneViewModel::class.java))
            return SortOneViewModel(
                captureRepository = container.captureRepository,
                spaceRepository = container.spaceRepository,
                suggestionDao = container.database.captureSuggestionDao(),
                confirmCaptureAction = container.confirmCaptureAction,
                captureResolution = container.captureResolution,
            ) as T
        }
    }
}
