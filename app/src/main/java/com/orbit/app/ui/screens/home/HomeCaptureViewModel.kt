package com.orbit.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.BrainDumpReminderStatus
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.R
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.domain.ai.OrbitAiRouter
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.BrainDumpActionResult
import com.orbit.app.domain.usecase.BrainDumpActionStatus
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.CaptureSuggestionLearningContext
import com.orbit.app.domain.usecase.CaptureSuggestionLearningDecision
import com.orbit.app.domain.usecase.RecordAiLearningEventUseCase
import com.orbit.app.domain.usecase.LearnedRuleProposal
import com.orbit.app.domain.usecase.ProposeLearnedRuleUseCase
import com.orbit.app.reminders.shouldScheduleNotification
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

private data class ConfirmedActionResult(
    val decision: CaptureSuggestionLearningDecision,
    val notificationSchedulingNeedsAttention: Boolean = false,
)

data class HomeCaptureUiState(
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val isPerformingAction: Boolean = false,
    val suggestion: CaptureSuggestion? = null,
    val brainDumpHandledItemIds: Set<String> = emptySet(),
    val message: String? = null,
    val notificationPermissionRequestPending: Boolean = false,
    val learnedRuleProposal: LearnedRuleProposal? = null,
)

class HomeCaptureViewModel(
    private val context: Context,
    private val captureRepository: CaptureRepository,
    private val brainDumpRepository: BrainDumpRepository,
    private val spaceRepository: SpaceRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val aiRouter: OrbitAiRouter,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val brainDumpActions: BrainDumpActions,
    private val reminderRepository: ReminderRepository,
    private val recordAiLearningEvent: RecordAiLearningEventUseCase,
    private val proposeLearnedRule: ProposeLearnedRuleUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private fun stringResource(@androidx.annotation.StringRes resId: Int): String = context.getString(resId)
    private val _uiState = MutableStateFlow(HomeCaptureUiState())
    val uiState: StateFlow<HomeCaptureUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<Long>(ActiveBrainDumpCaptureIdKey)?.let(::resumeBrainDump)
    }

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
                        message = stringResource(R.string.core_capture_message_save_failed),
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
                if (analysis.brainDumpItems.isNotEmpty()) {
                    persistBrainDumpSession(captureId, analysis, safeCalendarDateContext)
                    savedStateHandle[ActiveBrainDumpCaptureIdKey] = captureId
                }
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
                        message = stringResource(R.string.core_capture_message_safe_in_inbox),
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
                message = stringResource(R.string.core_capture_message_kept_in_inbox),
            )
        }
    }

    fun cancelSuggestion() {
        if (_uiState.value.isPerformingAction) return
        val suggestion = _uiState.value.suggestion ?: return
        if (suggestion.analysis.brainDumpItems.isNotEmpty()) {
            savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
            _uiState.update { it.copy(suggestion = null, brainDumpHandledItemIds = emptySet()) }
            return
        }

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            runCatching {
                archiveCancelledCapture(
                    captureRepository = captureRepository,
                    captureId = suggestion.captureId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        suggestion = null,
                        brainDumpHandledItemIds = emptySet(),
                        message = stringResource(R.string.core_capture_message_capture_cancelled),
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = stringResource(R.string.core_capture_message_cancel_failed),
                    )
                }
            }
        }
    }

    fun cancelBrainDump() {
        if (_uiState.value.isPerformingAction) return
        val suggestion = _uiState.value.suggestion ?: return
        if (suggestion.analysis.brainDumpItems.isEmpty()) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            runCatching {
                brainDumpActions.dismissCapture(suggestion.captureId, archive = true)
            }.onSuccess {
                savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        suggestion = null,
                        brainDumpHandledItemIds = emptySet(),
                        message = stringResource(R.string.core_capture_message_brain_dump_cancelled),
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = stringResource(R.string.core_capture_message_brain_dump_cancel_failed),
                    )
                }
            }
        }
    }

    fun resumeBrainDump(captureId: Long) {
        if (captureId <= 0L || _uiState.value.isPerformingAction) return
        savedStateHandle[ActiveBrainDumpCaptureIdKey] = captureId
        viewModelScope.launch {
            runCatching { loadBrainDumpSuggestion(captureId) }
                .onFailure {
                    savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
                    _uiState.update { state ->
                        state.copy(message = stringResource(R.string.core_capture_message_brain_dump_gone))
                    }
                }
        }
    }

    fun saveNote(title: String, spaceId: Long?) {
        performConfirmedAction(
            successMessage = stringResource(R.string.core_capture_message_saved_note),
        ) { suggestion ->
            confirmCaptureAction.saveNote(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                scheduledDateEpochDay = suggestion.calendarDateContextEpochDay,
            )
            ConfirmedActionResult(CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "save_note",
                finalType = SuggestedItemType.Note,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                sourceText = suggestion.analysis.rawText,
            ))
        }
    }

    fun createTask(title: String, dueAt: Long?, spaceId: Long?) {
        performConfirmedAction(
            successMessage = stringResource(R.string.core_capture_message_task_created),
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
            ConfirmedActionResult(CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "create_task",
                finalType = SuggestedItemType.Task,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                finalDueAt = dueAt,
                sourceText = suggestion.analysis.rawText,
            ))
        }
    }

    fun createReminder(
        title: String,
        dueAt: Long,
        spaceId: Long?,
        linkedTaskId: Long? = null,
    ) {
        performConfirmedAction(
            successMessage = stringResource(R.string.core_capture_message_reminder_created),
            requestNotificationPermission = true,
        ) { suggestion ->
            val reminderId = confirmCaptureAction.createReminder(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                dueAt = dueAt,
                linkedTaskId = linkedTaskId,
            )
            val schedulingNeedsAttention = reminderRepository.getById(reminderId)?.let { reminder ->
                reminder.shouldScheduleNotification() && reminder.notificationWorkId == null
            } ?: true
            ConfirmedActionResult(CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "create_reminder",
                finalType = SuggestedItemType.Reminder,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = title,
                finalDueAt = dueAt,
                sourceText = suggestion.analysis.rawText,
            ),
            notificationSchedulingNeedsAttention = schedulingNeedsAttention,
        )
    }

    fun saveBrainDumpItem(
        item: BrainDumpSuggestion,
        title: String,
        type: SuggestedItemType,
        dueAt: Long?,
        spaceId: Long?,
    ) {
        require(type == SuggestedItemType.Note || type == SuggestedItemType.Task)
        val cleanTitle = title.trim().ifBlank { item.title }
        performBrainDumpAction(
            item,
            stringResource(R.string.core_capture_message_saved_brain_dump_item),
        ) { suggestion ->
            val result = when (type) {
                SuggestedItemType.Note -> brainDumpActions.saveNote(
                    suggestion.captureId, item.id, cleanTitle, spaceId,
                )
                else -> brainDumpActions.saveTask(
                    suggestion.captureId, item.id, cleanTitle, dueAt = dueAt, spaceId = spaceId,
                )
            }
            result to CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.BrainDump,
                userAction = "save_brain_dump_item",
                finalType = type,
                finalSpaceId = spaceId,
                finalSpaceName = suggestion.spaceNameFor(spaceId),
                finalTitle = cleanTitle,
                finalDueAt = dueAt,
                sourceItemId = item.id,
                sourceText = item.rawText,
            )
        }
    }

    fun saveBrainDumpReminder(item: BrainDumpSuggestion, title: String, dueAt: Long, spaceId: Long?) {
        val cleanTitle = title.trim().ifBlank { item.title }
        performBrainDumpAction(
            item = item,
            successMessage = stringResource(R.string.core_capture_message_reminder_created),
            requestNotificationPermission = true,
        ) { suggestion ->
            brainDumpActions.saveReminder(suggestion.captureId, item.id, cleanTitle, dueAt, spaceId) to
                CaptureSuggestionLearningDecision(
                    surface = AiSuggestionSurface.BrainDump,
                    userAction = "create_brain_dump_reminder",
                    finalType = SuggestedItemType.Reminder,
                    finalSpaceId = spaceId,
                    finalSpaceName = suggestion.spaceNameFor(spaceId),
                    finalTitle = cleanTitle,
                    finalDueAt = dueAt,
                    sourceItemId = item.id,
                    sourceText = item.rawText,
                )
        }
    }

    fun saveBrainDumpOriginalForLater(item: BrainDumpSuggestion) {
        performBrainDumpAction(
            item,
            stringResource(R.string.core_capture_message_saved_line_for_later),
        ) { suggestion ->
            val result = brainDumpActions.saveOriginalLineForLater(suggestion.captureId, item.id)
            result to null
        }
    }

    fun skipBrainDumpItem(item: BrainDumpSuggestion) {
        performBrainDumpAction(
            item,
            stringResource(R.string.core_capture_message_skipped_suggestion),
            rejectionAction = "skip_brain_dump_item",
        ) { suggestion ->
            brainDumpActions.skip(suggestion.captureId, item.id) to null
        }
    }

    private fun performConfirmedAction(
        successMessage: String,
        requestNotificationPermission: Boolean = false,
        action: suspend (CaptureSuggestion) -> ConfirmedActionResult,
    ) {
        val suggestion = _uiState.value.suggestion ?: return
        if (_uiState.value.isPerformingAction) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            try {
                val result = action(suggestion)
                val learningProposal = recordLearningOutcome(suggestion.learningContext(), result.decision)
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        suggestion = null,
                        brainDumpHandledItemIds = emptySet(),
                        message = if (result.notificationSchedulingNeedsAttention) {
                            stringResource(R.string.core_capture_message_reminder_scheduling_attention)
                        } else {
                            successMessage
                        },
                        notificationPermissionRequestPending = requestNotificationPermission,
                        learnedRuleProposal = learningProposal,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = stringResource(R.string.core_capture_message_action_failed),
                    )
                }
            }
        }
    }

    private fun performBrainDumpAction(
        item: BrainDumpSuggestion,
        successMessage: String,
        requestNotificationPermission: Boolean = false,
        rejectionAction: String? = null,
        action: suspend (CaptureSuggestion) -> Pair<BrainDumpActionResult, CaptureSuggestionLearningDecision?>,
    ) {
        val suggestion = _uiState.value.suggestion ?: return
        if (_uiState.value.isPerformingAction || item.id in _uiState.value.brainDumpHandledItemIds) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            try {
                val (result, decision) = action(suggestion)
                if (result.status == BrainDumpActionStatus.Applied) {
                    if (decision != null) {
                        recordLearningOutcome(suggestion.learningContext(item), decision)
                    } else if (rejectionAction != null && item.id !in _uiState.value.brainDumpHandledItemIds) {
                        runCatching {
                            if (rejectionAction == "skip_brain_dump_item") {
                                recordAiLearningEvent.recordBrainDumpRejected(
                                    context = suggestion.learningContext(item),
                                    itemId = item.id,
                                    sourceText = item.rawText,
                                    suggestedType = item.suggestedType,
                                    suggestedSpaceName = item.suggestedSpaceName,
                                )
                            } else {
                                recordAiLearningEvent.recordRejected(
                                    context = suggestion.learningContext(item),
                                    userAction = rejectionAction,
                                    surface = AiSuggestionSurface.BrainDump,
                                    sourceItemId = item.id,
                                    sourceText = item.rawText,
                                )
                            }
                        }
                    }
                }
                if (result.sessionCompleted || result.status == BrainDumpActionStatus.Missing) {
                    savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            suggestion = null,
                            brainDumpHandledItemIds = emptySet(),
                            message = when {
                                result.status == BrainDumpActionStatus.Missing ->
                                    stringResource(R.string.core_capture_message_brain_dump_gone)
                                result.notificationScheduled == false ->
                                    stringResource(R.string.core_capture_message_brain_dump_scheduling_attention)
                                else -> stringResource(R.string.core_capture_message_brain_dump_reviewed)
                            },
                            notificationPermissionRequestPending =
                                requestNotificationPermission && result.reminderCreated,
                        )
                    }
                } else {
                    loadBrainDumpSuggestion(suggestion.captureId)
                    _uiState.update { state -> state.copy(
                        isPerformingAction = false,
                        message = if (result.notificationScheduled == false) {
                            stringResource(R.string.core_capture_message_reminder_scheduling_attention)
                        } else {
                            successMessage
                        },
                        notificationPermissionRequestPending =
                            requestNotificationPermission && result.reminderCreated,
                    ) }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = stringResource(R.string.core_capture_message_brain_dump_item_failed),
                    )
                }
            }
        }
    }

    private suspend fun persistBrainDumpSession(
        captureId: Long,
        analysis: CaptureAnalysis,
        calendarDateContextEpochDay: Long?,
    ) {
        val now = System.currentTimeMillis()
        brainDumpRepository.createSession(
            session = BrainDumpSessionEntity(
                captureId = captureId,
                analyzerSource = analysis.analyzerSource.name,
                calendarDateContextEpochDay = calendarDateContextEpochDay,
                createdAt = now,
                updatedAt = now,
            ),
            items = analysis.brainDumpItems.mapIndexed { index, item -> item.toEntity(captureId, index + 1, now) },
        )
    }

    private suspend fun loadBrainDumpSuggestion(captureId: Long) {
        val stored = requireNotNull(brainDumpRepository.getSession(captureId))
        val capture = requireNotNull(captureRepository.getById(captureId))
        val items = stored.items.map { item -> item.toSuggestion() }
        val spaces = loadSpaceOptions()
        val analysis = CaptureAnalysis(
            rawText = capture.rawText,
            suggestedType = SuggestedItemType.Note,
            suggestedSpaceName = "Inbox",
            suggestedTitle = "Brain Dump",
            summary = "A multi-part capture ready to review.",
            suggestedNextAction = "Review the split suggestions one at a time",
            relatedTopics = items.map { it.suggestedSpaceName }.distinct(),
            reminderPossible = items.any { it.suggestedType == SuggestedItemType.Reminder },
            confidence = 0.74f,
            analyzerSource = runCatching { CaptureAnalyzerSource.valueOf(stored.session.analyzerSource) }
                .getOrDefault(CaptureAnalyzerSource.Local),
            brainDumpItems = items,
        )
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                isPerformingAction = false,
                suggestion = CaptureSuggestion(
                    captureId = captureId,
                    suggestedSpaceId = null,
                    analysis = analysis,
                    spaceOptions = spaces,
                    calendarDateContextEpochDay = stored.session.calendarDateContextEpochDay,
                ),
                brainDumpHandledItemIds = stored.items
                    .filter { item -> item.outcome != BrainDumpItemOutcome.Pending }
                    .mapTo(linkedSetOf()) { item -> item.sourceKey },
            )
        }
    }

    private fun BrainDumpSuggestion.toEntity(
        captureId: Long,
        ordinal: Int,
        timestamp: Long,
    ) = BrainDumpItemEntity(
        captureId = captureId,
        sourceKey = id,
        ordinal = ordinal,
        rawText = rawText,
        suggestedTitle = title,
        suggestedType = suggestedType,
        suggestedSpaceName = suggestedSpaceName,
        confidence = confidence,
        tinyNextAction = tinyNextAction,
        reason = reason,
        reminderStatus = reminderTimeStatus.toStoredStatus(),
        suggestedReminderAt = suggestedReminderAt,
        reminderPhrase = reminderPhrase,
        createdAt = timestamp,
        updatedAt = timestamp,
    )

    private fun BrainDumpItemEntity.toSuggestion() = BrainDumpSuggestion(
        id = sourceKey,
        rawText = rawText,
        title = suggestedTitle,
        suggestedType = suggestedType,
        suggestedSpaceName = suggestedSpaceName,
        confidence = confidence,
        tinyNextAction = tinyNextAction,
        reason = reason,
        reminderTimeStatus = reminderStatus.toDomainStatus(),
        suggestedReminderAt = suggestedReminderAt,
        reminderPhrase = reminderPhrase,
    )

    private fun ReminderTimeStatus.toStoredStatus() = when (this) {
        ReminderTimeStatus.Unspecified -> BrainDumpReminderStatus.Unspecified
        ReminderTimeStatus.Resolved -> BrainDumpReminderStatus.Resolved
        ReminderTimeStatus.NeedsClarification -> BrainDumpReminderStatus.NeedsClarification
    }

    private fun BrainDumpReminderStatus.toDomainStatus() = when (this) {
        BrainDumpReminderStatus.Unspecified -> ReminderTimeStatus.Unspecified
        BrainDumpReminderStatus.Resolved -> ReminderTimeStatus.Resolved
        BrainDumpReminderStatus.NeedsClarification -> ReminderTimeStatus.NeedsClarification
    }

    fun saveLearnedRuleProposal() {
        val proposal = _uiState.value.learnedRuleProposal ?: return
        viewModelScope.launch {
            runCatching { proposeLearnedRule.save(proposal) }
                .onSuccess {
                    _uiState.update { it.copy(learnedRuleProposal = null) }
                }
                .onFailure {
                    _uiState.update { it.copy(message = stringResource(R.string.core_capture_message_preference_failed)) }
                }
        }
    }

    fun dismissLearnedRuleProposal() {
        _uiState.update { it.copy(learnedRuleProposal = null) }
    }

    private suspend fun recordLearningOutcome(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
    ): LearnedRuleProposal? = runCatching {
            if (recordAiLearningEvent.hasCorrections(context, decision)) {
                recordAiLearningEvent.recordCorrected(context, decision)
                proposeLearnedRule.proposalAfterCorrection()
            } else {
                recordAiLearningEvent.recordAccepted(context, decision)
                null
            }
        }.getOrNull()

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
                    message = stringResource(R.string.core_capture_message_notifications_off),
                )
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(
        private val context: Context,
        private val captureRepository: CaptureRepository,
        private val brainDumpRepository: BrainDumpRepository,
        private val spaceRepository: SpaceRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val aiRouter: OrbitAiRouter,
        private val confirmCaptureAction: ConfirmCaptureActionUseCase,
        private val brainDumpActions: BrainDumpActions,
        private val reminderRepository: ReminderRepository,
        private val recordAiLearningEvent: RecordAiLearningEventUseCase,
        private val proposeLearnedRule: ProposeLearnedRuleUseCase,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeCaptureViewModel::class.java))
            return HomeCaptureViewModel(
                context = context,
                captureRepository = captureRepository,
                brainDumpRepository = brainDumpRepository,
                spaceRepository = spaceRepository,
                appSettingsRepository = appSettingsRepository,
                aiRouter = aiRouter,
                confirmCaptureAction = confirmCaptureAction,
                brainDumpActions = brainDumpActions,
                reminderRepository = reminderRepository,
                recordAiLearningEvent = recordAiLearningEvent,
                proposeLearnedRule = proposeLearnedRule,
                savedStateHandle = savedStateHandle,
            ) as T
        }
    }

    private companion object {
        const val ActiveBrainDumpCaptureIdKey = "activeBrainDumpCaptureId"
    }
}

internal suspend fun archiveCancelledCapture(
    captureRepository: CaptureRepository,
    captureId: Long,
    now: Long = System.currentTimeMillis(),
) {
    val capture = captureRepository.getById(captureId) ?: return
    if (capture.status != CaptureStatus.Inbox) return
    captureRepository.update(
        capture.copy(
            status = CaptureStatus.Archived,
            updatedAt = now,
        ),
    )
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
