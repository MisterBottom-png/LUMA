package com.orbit.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.domain.ai.OrbitAiRouter
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.CaptureSuggestionLearningContext
import com.orbit.app.domain.usecase.CaptureSuggestionLearningDecision
import com.orbit.app.domain.usecase.RecordAiLearningEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CaptureSuggestion(
    val captureId: Long,
    val suggestedSpaceId: Long?,
    val analysis: CaptureAnalysis,
    val spaceOptions: List<CaptureSpaceOption>,
    val calendarDateContextEpochDay: Long? = null,
)

data class CaptureSpaceOption(
    val id: Long?,
    val name: String,
)

data class HomeCaptureUiState(
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val isPerformingAction: Boolean = false,
    val suggestion: CaptureSuggestion? = null,
    val brainDumpHandledItemIds: Set<String> = emptySet(),
    val message: String? = null,
    val mondayConfigured: Boolean = false,
    val notificationPermissionRequestPending: Boolean = false,
)

class HomeCaptureViewModel(
    private val captureRepository: CaptureRepository,
    private val spaceRepository: SpaceRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val aiRouter: OrbitAiRouter,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val recordAiLearningEvent: RecordAiLearningEventUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeCaptureUiState())
    val uiState: StateFlow<HomeCaptureUiState> = _uiState.asStateFlow()

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun analyzeCapture(calendarDateContextEpochDay: Long? = null): Boolean {
        val rawText = _uiState.value.inputText.trim()
        if (rawText.isBlank() || _uiState.value.isAnalyzing) return false
        val safeCalendarDateContext = calendarDateContextEpochDay
            ?.let { runCatching { LocalDate.ofEpochDay(it).toEpochDay() }.getOrNull() }

        _uiState.update { it.copy(isAnalyzing = true, message = null) }
        viewModelScope.launch {
            val capture = CaptureEntity(rawText = rawText, status = CaptureStatus.Inbox)
            val captureId = try {
                captureRepository.insert(capture)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        message = "I couldn't save that capture. Your text is still here.",
                    )
                }
                return@launch
            }

            // Clear only after the raw text is safely in the local Inbox.
            _uiState.update { it.copy(inputText = "") }

            val spaceOptions = loadSpaceOptions()
            try {
                val settings = appSettingsRepository.settings.first()
                val analysis = aiRouter.analyzeCapture(
                    rawText = rawText,
                    settings = settings,
                    allowedSpaces = spaceOptions.map { it.name },
                ).analysis
                val space = spaceOptions
                    .firstOrNull { it.name.equals(analysis.suggestedSpaceName, ignoreCase = true) }
                    ?.takeUnless { analysis.confidenceLevel == CaptureConfidence.Low }
                captureRepository.update(
                    capture.copy(
                        id = captureId,
                        suggestedType = analysis.suggestedType,
                        suggestedSpaceId = space?.id,
                    ),
                )
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        brainDumpHandledItemIds = emptySet(),
                        suggestion = CaptureSuggestion(
                            captureId = captureId,
                            suggestedSpaceId = space?.id,
                            analysis = analysis,
                            spaceOptions = spaceOptions,
                            calendarDateContextEpochDay = safeCalendarDateContext,
                        ),
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        brainDumpHandledItemIds = emptySet(),
                        suggestion = CaptureSuggestion(
                            captureId = captureId,
                            suggestedSpaceId = null,
                            analysis = manualFallbackAnalysis(rawText),
                            spaceOptions = spaceOptions,
                            calendarDateContextEpochDay = safeCalendarDateContext,
                        ),
                        message = "It's safe in your Inbox. You can still choose what to do.",
                    )
                }
            }
        }
        return true
    }

    fun keepInInbox() {
        if (_uiState.value.isPerformingAction) return
        _uiState.value.suggestion?.let { suggestion ->
            viewModelScope.launch {
                runCatching {
                    recordAiLearningEvent.recordRejected(
                        context = suggestion.learningContext(),
                        userAction = "keep_in_inbox",
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                suggestion = null,
                brainDumpHandledItemIds = emptySet(),
                message = "Kept in Inbox.",
            )
        }
    }

    fun cancelSuggestion() {
        if (_uiState.value.isPerformingAction) return
        _uiState.update { it.copy(suggestion = null, brainDumpHandledItemIds = emptySet()) }
    }

    fun saveNote(title: String, spaceId: Long?) {
        performConfirmedAction(
            successMessage = "Saved as a note.",
        ) { suggestion ->
            confirmCaptureAction.saveNote(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                scheduledDateEpochDay = suggestion.calendarDateContextEpochDay,
            )
            CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "save_note",
                finalType = SuggestedItemType.Note,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                sourceText = suggestion.analysis.rawText,
            )
        }
    }

    fun createTask(title: String, dueAt: Long?, spaceId: Long?) {
        performConfirmedAction(
            successMessage = "Task created.",
        ) { suggestion ->
            val finalSchedule = calendarTaskSchedule(
                dueAt = dueAt,
                calendarDateContextEpochDay = suggestion.calendarDateContextEpochDay,
            )
            confirmCaptureAction.createTask(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                dueAt = finalSchedule.dueAt,
                scheduledDateEpochDay = finalSchedule.scheduledDateEpochDay,
            )
            CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "create_task",
                finalType = SuggestedItemType.Task,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                finalDueAt = dueAt,
                sourceText = suggestion.analysis.rawText,
            )
        }
    }

    fun createReminder(
        title: String,
        dueAt: Long,
        spaceId: Long?,
        linkedTaskId: Long? = null,
    ) {
        performConfirmedAction(
            successMessage = "Reminder created.",
            requestNotificationPermission = true,
        ) { suggestion ->
            confirmCaptureAction.createReminder(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                dueAt = dueAt,
                linkedTaskId = linkedTaskId,
            )
            CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "create_reminder",
                finalType = SuggestedItemType.Reminder,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                finalDueAt = dueAt,
                sourceText = suggestion.analysis.rawText,
            )
        }
    }

    fun saveBrainDumpItem(
        item: BrainDumpSuggestion,
        title: String,
        type: SuggestedItemType,
        spaceId: Long?,
    ) {
        handleBrainDumpItem(item.id, successMessage = "Saved one Brain Dump item.") { suggestion ->
            val cleanTitle = title.trim().ifBlank { item.title }
            when (type) {
                SuggestedItemType.Task,
                SuggestedItemType.Reminder,
                SuggestedItemType.MondayItem,
                -> confirmCaptureAction.saveBrainDumpTask(
                    title = cleanTitle,
                    notes = item.rawText.takeUnless { it == cleanTitle }.orEmpty(),
                    spaceId = spaceId,
                )

                SuggestedItemType.Note -> confirmCaptureAction.saveBrainDumpNote(
                    title = cleanTitle,
                    body = item.rawText,
                    spaceId = spaceId,
                )
            }
            recordLearningOutcome(
                context = suggestion.learningContext(item),
                decision = CaptureSuggestionLearningDecision(
                    surface = AiSuggestionSurface.BrainDump,
                    userAction = "save_brain_dump_item",
                    finalType = type,
                    finalSpaceId = spaceId,
                    finalSpaceName = suggestion.spaceNameFor(spaceId),
                    finalTitle = cleanTitle,
                    sourceItemId = item.id,
                    sourceText = item.rawText,
                ),
            )
            suggestion
        }
    }

    fun keepBrainDumpItemInInbox(item: BrainDumpSuggestion, editedText: String) {
        handleBrainDumpItem(item.id, successMessage = "Kept one item in Inbox.") {
            captureRepository.insert(
                CaptureEntity(
                    rawText = editedText.trim().ifBlank { item.rawText },
                    status = CaptureStatus.Inbox,
                    suggestedType = item.suggestedType,
                ),
            )
            runCatching {
                recordAiLearningEvent.recordRejected(
                    context = it.learningContext(item),
                    userAction = "keep_brain_dump_item_in_inbox",
                    surface = AiSuggestionSurface.BrainDump,
                    sourceItemId = item.id,
                    sourceText = editedText.trim().ifBlank { item.rawText },
                )
            }
            it
        }
    }

    fun skipBrainDumpItem(item: BrainDumpSuggestion) {
        handleBrainDumpItem(item.id, successMessage = "Skipped one suggestion.") { suggestion ->
            runCatching {
                recordAiLearningEvent.recordBrainDumpRejected(
                    context = suggestion.learningContext(item),
                    itemId = item.id,
                    sourceText = item.rawText,
                    suggestedType = item.suggestedType,
                    suggestedSpaceName = item.suggestedSpaceName,
                )
            }
            suggestion
        }
    }

    private fun performConfirmedAction(
        successMessage: String,
        requestNotificationPermission: Boolean = false,
        action: suspend (CaptureSuggestion) -> CaptureSuggestionLearningDecision,
    ) {
        val suggestion = _uiState.value.suggestion ?: return
        if (_uiState.value.isPerformingAction) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            try {
                val decision = action(suggestion)
                recordLearningOutcome(suggestion.learningContext(), decision)
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        suggestion = null,
                        brainDumpHandledItemIds = emptySet(),
                        message = successMessage,
                        notificationPermissionRequestPending = requestNotificationPermission,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = "That action didn't finish. Your capture is still in the Inbox.",
                    )
                }
            }
        }
    }

    private fun handleBrainDumpItem(
        itemId: String,
        successMessage: String,
        action: suspend (CaptureSuggestion) -> CaptureSuggestion,
    ) {
        val suggestion = _uiState.value.suggestion ?: return
        if (_uiState.value.isPerformingAction || itemId in _uiState.value.brainDumpHandledItemIds) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            try {
                val activeSuggestion = action(suggestion)
                val handledIds = _uiState.value.brainDumpHandledItemIds + itemId
                val allHandled = handledIds.size >= activeSuggestion.analysis.brainDumpItems.size
                if (allHandled) {
                    confirmCaptureAction.markCaptureReviewed(activeSuggestion.captureId)
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            suggestion = null,
                            brainDumpHandledItemIds = emptySet(),
                            message = "Brain Dump reviewed. The original capture is still saved.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            brainDumpHandledItemIds = handledIds,
                            message = successMessage,
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = "That item did not save. The original dump is still in Inbox.",
                    )
                }
            }
        }
    }

    private suspend fun recordLearningOutcome(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
    ) {
        runCatching {
            if (recordAiLearningEvent.hasCorrections(context, decision)) {
                recordAiLearningEvent.recordCorrected(context, decision)
            } else {
                recordAiLearningEvent.recordAccepted(context, decision)
            }
        }
    }

    private suspend fun loadSpaceOptions(): List<CaptureSpaceOption> {
        val activeSpaces = spaceRepository.observeAll()
            .first()
            .filterNot { it.hidden || it.archived }
            .sortedBy { it.sortOrder }
            .map { CaptureSpaceOption(id = it.id, name = it.name) }
        return listOf(CaptureSpaceOption(id = null, name = "Inbox")) + activeSpaces
    }

    private fun CaptureSuggestion.learningContext(): CaptureSuggestionLearningContext =
        CaptureSuggestionLearningContext(
            captureId = captureId,
            analysis = analysis,
            suggestedSpaceId = suggestedSpaceId,
        )

    private fun CaptureSuggestion.learningContext(item: BrainDumpSuggestion): CaptureSuggestionLearningContext =
        CaptureSuggestionLearningContext(
            captureId = captureId,
            analysis = analysis.copy(
                rawText = item.rawText,
                suggestedType = item.suggestedType,
                suggestedSpaceName = item.suggestedSpaceName,
                suggestedTitle = item.title,
                suggestedNextAction = item.tinyNextAction,
                relatedTopics = listOf(item.suggestedSpaceName),
                reminderPossible = false,
                suggestedReminderAt = null,
                confidence = item.confidence,
                typeReason = item.reason,
                spaceReason = "Brain Dump item suggested for ${item.suggestedSpaceName}.",
                brainDumpItems = emptyList(),
            ),
            suggestedSpaceId = spaceOptions
                .firstOrNull { it.name.equals(item.suggestedSpaceName, ignoreCase = true) }
                ?.id,
        )

    private fun CaptureSuggestion.spaceNameFor(spaceId: Long?): String =
        spaceOptions.firstOrNull { it.id == spaceId }?.name ?: "Inbox"

    private fun manualFallbackAnalysis(rawText: String): CaptureAnalysis = CaptureAnalysis(
        rawText = rawText,
        suggestedType = SuggestedItemType.Note,
        suggestedSpaceName = "Inbox",
        possibleMondayItem = false,
        suggestedNextAction = "Keep this in Inbox for now",
        relatedTopics = listOf("Inbox"),
        reminderPossible = false,
        confidence = 0.18f,
        typeReason = "Analysis paused, so no type is being forced.",
        spaceReason = "Inbox keeps the raw capture safe until you choose.",
        analyzerFailed = true,
    )

    fun notificationPermissionRequestStarted() {
        _uiState.update { it.copy(notificationPermissionRequestPending = false) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update {
                it.copy(
                    message = "Reminder saved. Notifications are off; it is still available in Review.",
                )
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(
        private val captureRepository: CaptureRepository,
        private val spaceRepository: SpaceRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val aiRouter: OrbitAiRouter,
        private val confirmCaptureAction: ConfirmCaptureActionUseCase,
        private val recordAiLearningEvent: RecordAiLearningEventUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeCaptureViewModel::class.java))
            return HomeCaptureViewModel(
                captureRepository = captureRepository,
                spaceRepository = spaceRepository,
                appSettingsRepository = appSettingsRepository,
                aiRouter = aiRouter,
                confirmCaptureAction = confirmCaptureAction,
                recordAiLearningEvent = recordAiLearningEvent,
            ) as T
        }
    }
}

internal data class CalendarTaskSchedule(
    val dueAt: Long?,
    val scheduledDateEpochDay: Long?,
)

internal fun calendarTaskSchedule(
    dueAt: Long?,
    calendarDateContextEpochDay: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CalendarTaskSchedule {
    val contextDate = calendarDateContextEpochDay
        ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
    val dueDate = dueAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
    val usesContext = contextDate != null && dueDate == contextDate
    return CalendarTaskSchedule(
        dueAt = dueAt.takeUnless { usesContext },
        scheduledDateEpochDay = contextDate?.toEpochDay().takeIf { usesContext },
    )
}
