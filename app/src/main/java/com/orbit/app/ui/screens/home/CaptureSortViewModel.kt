package com.orbit.app.ui.screens.home

import com.orbit.app.domain.capture.isTaskLike
import com.orbit.app.domain.capture.taskDateEpochDay
import com.orbit.app.domain.capture.reminderTime
import com.orbit.app.ui.reminders.messageRes
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.reminders.ReminderSaveOutcome
import androidx.core.content.ContextCompat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.orbit.app.R
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.BrainDumpReminderStatus
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.capture.toAnalysis
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.BrainDumpActionResult
import com.orbit.app.domain.usecase.BrainDumpActionStatus
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.CaptureSuggestionLearningContext
import com.orbit.app.domain.usecase.CaptureSuggestionLearningDecision
import com.orbit.app.domain.usecase.RecordAiLearningEventUseCase
import com.orbit.app.domain.usecase.LearnedRuleProposal
import com.orbit.app.domain.usecase.ProposeLearnedRuleUseCase
import com.orbit.app.ui.localization.effectiveAppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.orbit.app.domain.analyzer.CaptureAnalyzer
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import com.orbit.app.domain.analyzer.captureLifeSignalOf
import com.orbit.app.domain.analyzer.BrainDumpSplitter
import com.orbit.app.domain.capture.ThoughtSplitter
import com.orbit.app.domain.capture.toEntity
import com.orbit.app.data.local.entity.TaskStatus

data class CaptureSuggestion(
    val captureId: Long,
    val suggestedSpaceId: Long?,
    val analysis: CaptureAnalysis,
    val spaceOptions: List<CaptureSpaceOption>,
    val calendarDateContextEpochDay: Long? = null,
    /** Open straight into reminder setup (a time-sensitive thought that needs a time). */
    val startWithReminderSetup: Boolean = false,
    /** "Looks like N thoughts": offered only, never split without the user. */
    val possibleThoughts: Int = 0,
)

data class CaptureSpaceOption(
    val id: Long?,
    val name: String,
)

private data class ConfirmedActionResult(
    val decision: CaptureSuggestionLearningDecision,
    val resolved: ResolvedCapture? = null,
)

internal data class CaptureSortUiState(
    val isLoading: Boolean = false,
    val isPerformingAction: Boolean = false,
    val suggestion: CaptureSuggestion? = null,
    val brainDumpHandledItemIds: Set<String> = emptySet(),
    val brainDumpInteraction: BrainDumpInteractionState? = null,
    val message: String? = null,
    val notificationPermissionRequestPending: Boolean = false,
    val learnedRuleProposal: LearnedRuleProposal? = null,
    /** Set once a capture was sorted, so the host can offer Undo. */
    val lastResolved: ResolvedCapture? = null,
)

/** What the user just turned a capture into; used for Undo in To sort. */
data class ResolvedCapture(
    val captureId: Long,
    val itemType: SuggestedItemType,
    val itemId: Long,
    /** For a reminder: whether it can actually reach the user. */
    val reminderOutcome: ReminderSaveOutcome? = null,
)

/**
 * Sorting one saved capture: shows LUMA's stored suggestion and lets the user
 * confirm or change it. Used by Review > To sort, by the optional "Sort right
 * after saving" setting, and for the one quick time question on Home.
 */
class CaptureSortViewModel(
    private val captureRepository: CaptureRepository,
    private val brainDumpRepository: BrainDumpRepository,
    private val spaceRepository: SpaceRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val brainDumpActions: BrainDumpActions,
    private val reminderRepository: ReminderRepository,
    private val recordAiLearningEvent: RecordAiLearningEventUseCase,
    private val proposeLearnedRule: ProposeLearnedRuleUseCase,
    private val captureInbox: CaptureInbox,
    private val suggestionDao: CaptureSuggestionDao,
    private val captureResolution: CaptureResolution,
    private val savedStateHandle: SavedStateHandle,
    private val applicationContext: Context,
    private val captureAnalyzer: CaptureAnalyzer,
    private val thoughtSplitter: ThoughtSplitter,
    private val reminderSaveOutcomes: ReminderSaveOutcomes,
) : ViewModel() {
    // Resolved on every use so messages follow a language change made while open.
    private val localizedContext: Context
        get() = applicationContext.createConfigurationContext(
            Configuration(applicationContext.resources.configuration).apply {
                setLocale(effectiveAppLocale(applicationContext))
            },
        )
    private val _uiState = MutableStateFlow(CaptureSortUiState())
    internal val uiState: StateFlow<CaptureSortUiState> = _uiState.asStateFlow()
    private val reminderOutcomeReporter = ReminderOutcomeReporter(
        outcomes = reminderSaveOutcomes,
        permissionGranted = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        },
        requestPermission = { _uiState.update { it.copy(notificationPermissionRequestPending = true) } },
    )
    private val brainDumpFlowCoordinator = BrainDumpFlowCoordinator(
        scope = viewModelScope,
        commit = ::commitBrainDumpRequest,
        discardRemaining = { captureId ->
            brainDumpActions.dismissCapture(captureId, archive = true)
        },
        onClose = ::onBrainDumpFlowClosed,
    )

    init {
        viewModelScope.launch {
            brainDumpFlowCoordinator.state.collect { interaction ->
                _uiState.update { it.copy(brainDumpInteraction = interaction) }
            }
        }
        savedStateHandle.get<Long>(ActiveBrainDumpCaptureIdKey)?.let(::resumeBrainDump)
            ?: savedStateHandle.get<Long>(OpenCaptureIdKey)?.let { open(it) }
    }

    /**
     * Opens the sorting sheet for a saved capture with LUMA's stored suggestion.
     * When no suggestion exists yet it is produced now; if that fails the user can
     * still sort by hand.
     */
    fun open(captureId: Long, startWithReminderSetup: Boolean = false) {
        if (captureId <= 0L || _uiState.value.isPerformingAction) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastResolved = null) }
            try {
                val capture = captureRepository.getById(captureId)
                if (capture == null || capture.status != CaptureStatus.Inbox) {
                    _uiState.update {
                        it.copy(isLoading = false, message = localized(R.string.core_sort_already_sorted))
                    }
                    return@launch
                }
                if (brainDumpRepository.getSession(captureId) == null && suggestionDao.getByCaptureId(captureId) == null) {
                    captureInbox.analyze(captureId)
                }
                if (brainDumpRepository.getSession(captureId) != null) {
                    savedStateHandle[OpenCaptureIdKey] = null
                    resumeBrainDump(captureId)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                savedStateHandle[OpenCaptureIdKey] = captureId
                val spaceOptions = loadSpaceOptions()
                val stored = suggestionDao.getByCaptureId(captureId)
                val analysis = stored?.toAnalysis(capture.rawText) ?: manualFallbackAnalysis(capture.rawText)
                // A Space the user picked when saving wins over the suggested one.
                val space = spaceOptions.firstOrNull { capture.suggestedSpaceId != null && it.id == capture.suggestedSpaceId }
                    ?: spaceOptions.firstOrNull {
                        stored?.suggestedSpaceName != null && it.name.equals(stored.suggestedSpaceName, ignoreCase = true)
                    }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        brainDumpHandledItemIds = emptySet(),
                        suggestion = CaptureSuggestion(
                            captureId = captureId,
                            suggestedSpaceId = space?.id,
                            analysis = analysis,
                            spaceOptions = spaceOptions,
                            // The thought's own Calendar day. Thoughts saved before it was
                            // kept on the thought have it only on a non-task suggestion.
                            calendarDateContextEpochDay = capture.contextDateEpochDay
                                ?: stored?.takeUnless { it.suggestedType.isTaskLike() }?.contextDateEpochDay,
                            startWithReminderSetup = startWithReminderSetup,
                            possibleThoughts = runCatching { thoughtSplitter.possibleThoughts(capture.rawText) }.getOrDefault(0),
                        ),
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, message = localized(R.string.core_sort_open_failed))
                }
            }
        }
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
                message = localized(R.string.core_home_message_kept_in_inbox),
            )
        }
        savedStateHandle[OpenCaptureIdKey] = null
    }

    /**
     * Closing the sheet (Cancel, swipe down, tap outside, back) never archives a sent
     * thought: it stays in the Inbox until the user decides what it becomes.
     */
    fun cancelSuggestion() {
        if (_uiState.value.isPerformingAction) return
        keepInInbox()
    }

    fun resumeBrainDump(captureId: Long) {
        if (captureId <= 0L || _uiState.value.isPerformingAction) return
        savedStateHandle[ActiveBrainDumpCaptureIdKey] = captureId
        viewModelScope.launch {
            runCatching { loadBrainDumpSuggestion(captureId) }
                .onFailure {
                    savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
                    _uiState.update { state ->
                        state.copy(message = localized(R.string.core_home_message_brain_dump_unavailable))
                    }
                }
        }
    }

    internal fun toggleBrainDumpRow(sourceKey: String) = brainDumpFlowCoordinator.toggleRow(sourceKey)

    internal fun openBrainDumpRow(sourceKey: String) = brainDumpFlowCoordinator.openRow(sourceKey)

    internal fun backToBrainDumpOverview() = brainDumpFlowCoordinator.backToOverview()

    internal fun saveTickedBrainDumpRows() {
        viewModelScope.launch { brainDumpFlowCoordinator.saveTicked() }
    }

    internal fun keepBrainDumpAsOneNote() {
        val title = _uiState.value.suggestion?.analysis?.rawText
            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(90).orEmpty()
        viewModelScope.launch { brainDumpFlowCoordinator.keepRestAsOneNote(title) }
    }

    /** "Split this list": a heading with a list under it becomes one thought per list line. */
    internal fun splitBrainDumpRow(sourceKey: String) {
        val suggestion = _uiState.value.suggestion ?: return
        val item = suggestion.analysis.brainDumpItems.firstOrNull { it.id == sourceKey } ?: return
        val lines = BrainDumpSplitter.listItemsOf(item.rawText)
        if (lines.size < 2) return
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val parts = captureAnalyzer.brainDumpItemsFor(lines)
                .mapIndexed { index, part -> part.toEntity(suggestion.captureId, index + 1, timestamp) }
            val status = runCatching {
                brainDumpActions.replaceWithParts(suggestion.captureId, sourceKey, parts)
            }.getOrNull()
            if (status == BrainDumpActionStatus.Applied) {
                runCatching { loadBrainDumpSuggestion(suggestion.captureId) }
            } else {
                _uiState.update { it.copy(message = localized(R.string.core_home_message_brain_dump_item_save_failed)) }
            }
        }
    }

    /**
     * "Looks like 3 thoughts · Split": makes a Brain Dump from one saved thought, only
     * because the user asked. Then the overview opens.
     */
    fun splitIntoThoughts(captureId: Long) {
        if (captureId <= 0L || _uiState.value.isPerformingAction) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = runCatching { thoughtSplitter.split(captureId) }.getOrDefault(ThoughtSplitter.Result.Unavailable)
            _uiState.update { it.copy(isLoading = false) }
            when (result) {
                ThoughtSplitter.Result.Split, ThoughtSplitter.Result.AlreadySplit -> {
                    savedStateHandle[OpenCaptureIdKey] = null
                    _uiState.update { it.copy(suggestion = null) }
                    resumeBrainDump(captureId)
                }
                ThoughtSplitter.Result.OneThought -> _uiState.update {
                    it.copy(message = localized(R.string.split_one_thought))
                }
                ThoughtSplitter.Result.Unavailable -> _uiState.update {
                    it.copy(message = localized(R.string.core_sort_open_failed))
                }
            }
        }
    }

    internal fun editBrainDumpItem() {
        brainDumpFlowCoordinator.edit()
    }

    internal fun updateBrainDumpDraft(draft: BrainDumpDraft) {
        brainDumpFlowCoordinator.updateDraft(draft)
    }

    internal fun continueBrainDumpFromEditor() {
        brainDumpFlowCoordinator.continueFromEditor()
    }

    internal fun stepBackBrainDump() {
        brainDumpFlowCoordinator.stepBack()
    }

    internal fun discardBrainDumpDraftChanges() {
        brainDumpFlowCoordinator.discardDraftChanges()
    }

    internal fun commitBrainDumpPrimaryAction() {
        viewModelScope.launch { brainDumpFlowCoordinator.commitPrimary() }
    }

    internal fun keepBrainDumpInInbox() {
        viewModelScope.launch { brainDumpFlowCoordinator.keepInInbox() }
    }

    internal fun skipBrainDump() {
        viewModelScope.launch { brainDumpFlowCoordinator.skip() }
    }

    internal fun undoBrainDumpSkip() {
        brainDumpFlowCoordinator.undoSkip()
    }

    internal fun retryBrainDumpAction() {
        viewModelScope.launch { brainDumpFlowCoordinator.retry() }
    }

    internal fun finishBrainDumpLater() {
        viewModelScope.launch { brainDumpFlowCoordinator.finishLater() }
    }

    internal fun closeBrainDumpCompletion() {
        viewModelScope.launch { brainDumpFlowCoordinator.closeCompletion() }
    }

    internal fun discardRemainingBrainDumpSuggestions() {
        viewModelScope.launch { brainDumpFlowCoordinator.discardRemaining() }
    }

    fun saveNote(title: String, spaceId: Long?, labelNames: List<String> = emptyList()) {
        performConfirmedAction(
            successMessage = localized(R.string.core_home_message_note_saved),
        ) { suggestion ->
            val noteId = confirmCaptureAction.saveNote(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                scheduledDateEpochDay = suggestion.calendarDateContextEpochDay,
                labelNames = labelNames,
            )
            ConfirmedActionResult(
                CaptureSuggestionLearningDecision(
                    surface = AiSuggestionSurface.Capture,
                    userAction = "save_note",
                    finalType = SuggestedItemType.Note,
                    finalSpaceId = spaceId,
                    finalSpaceName = suggestion.spaceNameFor(spaceId),
                    finalTitle = title,
                    sourceText = suggestion.analysis.rawText,
                ),
                resolved = ResolvedCapture(suggestion.captureId, SuggestedItemType.Note, noteId),
            )
        }
    }

    internal fun createTask(title: String, due: TaskDue, spaceId: Long?, labelNames: List<String> = emptyList()) {
        performConfirmedAction(
            successMessage = localized(R.string.core_home_message_task_created),
        ) { suggestion ->
            val taskId = confirmCaptureAction.createTask(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                dueAt = due.at,
                scheduledDateEpochDay = due.dayEpochDay,
                labelNames = labelNames,
            )
            ConfirmedActionResult(
                CaptureSuggestionLearningDecision(
                    surface = AiSuggestionSurface.Capture,
                    userAction = "create_task",
                    finalType = SuggestedItemType.Task,
                    finalSpaceId = spaceId,
                    finalSpaceName = suggestion.spaceNameFor(spaceId),
                    finalTitle = title,
                    finalDueAt = due.at,
                    sourceText = suggestion.analysis.rawText,
                ),
                resolved = ResolvedCapture(suggestion.captureId, SuggestedItemType.Task, taskId),
            )
        }
    }

    fun createReminder(
        title: String,
        dueAt: Long,
        spaceId: Long?,
        linkedTaskId: Long? = null,
        labelNames: List<String> = emptyList(),
    ) {
        performConfirmedAction(
            successMessage = localized(R.string.reminder_outcome_saved),
        ) { suggestion ->
            val reminderId = confirmCaptureAction.createReminder(
                captureId = suggestion.captureId,
                spaceId = spaceId,
                title = title,
                dueAt = dueAt,
                linkedTaskId = linkedTaskId,
                labelNames = labelNames,
            )
            val outcome = reminderOutcomeReporter.report(reminderId)
            ConfirmedActionResult(
                decision = CaptureSuggestionLearningDecision(
                    surface = AiSuggestionSurface.Capture,
                    userAction = "create_reminder",
                    finalType = SuggestedItemType.Reminder,
                    finalSpaceId = spaceId,
                    finalSpaceName = suggestion.spaceNameFor(spaceId),
                    finalTitle = title,
                    finalDueAt = dueAt,
                    sourceText = suggestion.analysis.rawText,
                ),
                resolved = ResolvedCapture(suggestion.captureId, SuggestedItemType.Reminder, reminderId, outcome),
            )
        }
    }

    private fun performConfirmedAction(
        successMessage: String,
        action: suspend (CaptureSuggestion) -> ConfirmedActionResult,
    ) {
        val suggestion = _uiState.value.suggestion ?: return
        if (_uiState.value.isPerformingAction) return

        _uiState.update { it.copy(isPerformingAction = true, message = null) }
        viewModelScope.launch {
            try {
                val result = action(suggestion)
                savedStateHandle[OpenCaptureIdKey] = null
                val learningProposal = recordLearningOutcome(suggestion.learningContext(), result.decision)
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        suggestion = null,
                        brainDumpHandledItemIds = emptySet(),
                        message = result.resolved?.reminderOutcome
                            ?.let { outcome -> localized(outcome.messageRes()) }
                            ?: successMessage,
                        learnedRuleProposal = learningProposal,
                        lastResolved = result.resolved,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPerformingAction = false,
                        message = localized(R.string.core_home_message_capture_action_failed),
                    )
                }
            }
        }
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
            suggestedTitle = localized(R.string.core_capture_brain_dump_title),
            summary = localized(R.string.core_home_brain_dump_resume_summary),
            suggestedNextAction = localized(R.string.core_home_brain_dump_review_split),
            relatedTopics = items.map { it.suggestedSpaceName }.distinct(),
            reminderPossible = items.any { it.suggestedType == SuggestedItemType.Reminder },
            confidence = 0.74f,
            analyzerSource = runCatching { CaptureAnalyzerSource.valueOf(stored.session.analyzerSource) }
                .getOrDefault(CaptureAnalyzerSource.Local),
            brainDumpItems = items,
        )
        _uiState.update {
            it.copy(
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
        brainDumpFlowCoordinator.start(
            captureId = captureId,
            items = items,
            spaces = spaces,
            storedOutcomes = stored.items.associate { item -> item.sourceKey to item.outcome },
            startWithOverview = true,
        )
    }

    private suspend fun commitBrainDumpRequest(
        request: BrainDumpCommitRequest,
    ): BrainDumpActionResult {
        val result = when (request) {
            is BrainDumpCommitRequest.SaveNote -> brainDumpActions.saveNote(
                request.captureId,
                request.sourceKey,
                request.draft.title,
                request.draft.spaceId,
            )
            is BrainDumpCommitRequest.SaveTask -> brainDumpActions.saveTask(
                request.captureId,
                request.sourceKey,
                request.draft.title,
                request.draft.scheduledAt.takeIf { request.draft.scheduledDateEpochDay == null },
                request.draft.spaceId,
                status = taskStatusFor(request.sourceKey),
                scheduledDateEpochDay = request.draft.scheduledDateEpochDay,
            )
            is BrainDumpCommitRequest.SaveReminder -> brainDumpActions.saveReminder(
                request.captureId,
                request.sourceKey,
                request.draft.title,
                request.targetAt,
                request.draft.spaceId,
            )
            is BrainDumpCommitRequest.KeepInInbox -> brainDumpActions.saveOriginalLineForLater(
                request.captureId,
                request.sourceKey,
            )
            is BrainDumpCommitRequest.Skip -> brainDumpActions.skip(
                request.captureId,
                request.sourceKey,
            )
            is BrainDumpCommitRequest.KeepRestAsOneNote -> brainDumpActions.keepRemainingAsOneNote(
                request.captureId,
                request.title,
            )
        }
        if (result.status == BrainDumpActionStatus.Applied) {
            recordBrainDumpLearning(request)
            val reminderId = result.reminderId
            if (request is BrainDumpCommitRequest.SaveReminder && reminderId != null) {
                return result.copy(reminderOutcome = reminderOutcomeReporter.report(reminderId))
            }
        } else if (result.status == BrainDumpActionStatus.Missing) {
            savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
            _uiState.update {
                it.copy(
                    suggestion = null,
                    brainDumpHandledItemIds = emptySet(),
                    message = localized(R.string.core_home_message_brain_dump_unavailable),
                )
            }
        }
        return result
    }

    private suspend fun recordBrainDumpLearning(request: BrainDumpCommitRequest) {
        val suggestion = _uiState.value.suggestion ?: return
        val item = suggestion.analysis.brainDumpItems.firstOrNull { it.id == request.sourceKey } ?: return
        when (request) {
            is BrainDumpCommitRequest.SaveNote,
            is BrainDumpCommitRequest.SaveTask,
            is BrainDumpCommitRequest.SaveReminder -> {
                val draft = when (request) {
                    is BrainDumpCommitRequest.SaveNote -> request.draft
                    is BrainDumpCommitRequest.SaveTask -> request.draft
                    is BrainDumpCommitRequest.SaveReminder -> request.draft
                    else -> error("Unreachable")
                }
                recordLearningOutcome(
                    suggestion.learningContext(item),
                    CaptureSuggestionLearningDecision(
                        surface = AiSuggestionSurface.BrainDump,
                        userAction = when (request) {
                            is BrainDumpCommitRequest.SaveNote -> "save_brain_dump_item"
                            is BrainDumpCommitRequest.SaveTask -> "save_brain_dump_item"
                            is BrainDumpCommitRequest.SaveReminder -> "create_brain_dump_reminder"
                            else -> error("Unreachable")
                        },
                        finalType = draft.type,
                        finalSpaceId = draft.spaceId,
                        finalSpaceName = suggestion.spaceNameFor(draft.spaceId),
                        finalTitle = draft.title,
                        finalDueAt = draft.scheduledAt,
                        sourceItemId = item.id,
                        sourceText = item.rawText,
                    ),
                )
            }
            is BrainDumpCommitRequest.Skip -> runCatching {
                recordAiLearningEvent.recordBrainDumpRejected(
                    context = suggestion.learningContext(item),
                    itemId = item.id,
                    sourceText = item.rawText,
                    suggestedType = item.suggestedType,
                    suggestedSpaceName = item.suggestedSpaceName,
                )
            }
            is BrainDumpCommitRequest.KeepInInbox,
            is BrainDumpCommitRequest.KeepRestAsOneNote,
            -> Unit
        }
    }

    private fun onBrainDumpFlowClosed(resumable: Boolean) {
        savedStateHandle[ActiveBrainDumpCaptureIdKey] = null
        _uiState.update {
            it.copy(
                suggestion = null,
                brainDumpHandledItemIds = emptySet(),
                brainDumpInteraction = null,
            )
        }
    }

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
        suggestedReminderAt = reminderTime(),
        taskDateEpochDay = taskDateEpochDay(),
        reminderPhrase = reminderPhrase,
        // Not stored: read again from the thought's words, in the app's language.
        lifeSignal = captureLifeSignalOf(rawText, effectiveAppLocale(applicationContext)),
    )

    /** "Someday" and "Waiting for" are kept as that task status. */
    private fun taskStatusFor(sourceKey: String): TaskStatus =
        when (_uiState.value.suggestion?.analysis?.brainDumpItems?.firstOrNull { it.id == sourceKey }?.lifeSignal) {
            CaptureLifeSignal.Someday -> TaskStatus.Someday
            CaptureLifeSignal.WaitingFor -> TaskStatus.WaitingFor
            else -> TaskStatus.Open
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
                    _uiState.update {
                        it.copy(message = localized(R.string.core_home_message_preference_save_failed))
                    }
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
        suggestedNextAction = localized(R.string.core_home_fallback_next_action),
        relatedTopics = listOf("Inbox"),
        reminderPossible = false,
        confidence = 0.18f,
        typeReason = localized(R.string.core_home_fallback_type_reason),
        spaceReason = localized(R.string.core_home_fallback_space_reason),
        analyzerFailed = true,
    )

    fun notificationPermissionRequestStarted() {
        _uiState.update { it.copy(notificationPermissionRequestPending = false) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        reminderOutcomeReporter.onPermissionAnswer(granted)
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Clears [shown] only if it is still the current message, so a newer one is kept. */
    fun messageShown(shown: String?) {
        if (shown == null) return
        _uiState.update { if (it.message == shown) it.copy(message = null) else it }
    }

    fun resolvedHandled() {
        _uiState.update { it.copy(lastResolved = null) }
    }

    /** Clears [handled] only if it is still the latest sorted thought. */
    fun resolvedHandled(handled: ResolvedCapture) {
        _uiState.update { if (it.lastResolved == handled) it.copy(lastResolved = null) else it }
    }

    /** Undo right after sorting: the new item is removed and the thought is back in To sort. */
    fun undo(resolved: ResolvedCapture) {
        viewModelScope.launch {
            runCatching { captureResolution.undo(resolved.captureId, resolved.itemType, resolved.itemId) }
                .onSuccess { _uiState.update { it.copy(message = localized(R.string.core_sort_undone)) } }
                .onFailure { _uiState.update { it.copy(message = localized(R.string.core_sort_undo_failed)) } }
        }
    }

    class Factory(
        private val captureRepository: CaptureRepository,
        private val brainDumpRepository: BrainDumpRepository,
        private val spaceRepository: SpaceRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val confirmCaptureAction: ConfirmCaptureActionUseCase,
        private val brainDumpActions: BrainDumpActions,
        private val reminderRepository: ReminderRepository,
        private val recordAiLearningEvent: RecordAiLearningEventUseCase,
        private val proposeLearnedRule: ProposeLearnedRuleUseCase,
        private val captureInbox: CaptureInbox,
        private val suggestionDao: CaptureSuggestionDao,
        private val captureResolution: CaptureResolution,
        private val savedStateHandle: SavedStateHandle,
        private val applicationContext: Context,
        private val captureAnalyzer: CaptureAnalyzer,
        private val thoughtSplitter: ThoughtSplitter,
        private val reminderSaveOutcomes: ReminderSaveOutcomes,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CaptureSortViewModel::class.java))
            return CaptureSortViewModel(
                captureRepository = captureRepository,
                brainDumpRepository = brainDumpRepository,
                spaceRepository = spaceRepository,
                appSettingsRepository = appSettingsRepository,
                confirmCaptureAction = confirmCaptureAction,
                brainDumpActions = brainDumpActions,
                reminderRepository = reminderRepository,
                recordAiLearningEvent = recordAiLearningEvent,
                proposeLearnedRule = proposeLearnedRule,
                captureInbox = captureInbox,
                suggestionDao = suggestionDao,
                captureResolution = captureResolution,
                savedStateHandle = savedStateHandle,
                applicationContext = applicationContext,
                captureAnalyzer = captureAnalyzer,
                thoughtSplitter = thoughtSplitter,
                reminderSaveOutcomes = reminderSaveOutcomes,
            ) as T
        }
    }

    companion object {
        fun factory(
            container: com.orbit.app.OrbitContainer,
            savedStateHandle: SavedStateHandle,
        ) = Factory(
            captureRepository = container.captureRepository,
            brainDumpRepository = container.brainDumpRepository,
            spaceRepository = container.spaceRepository,
            appSettingsRepository = container.appSettingsRepository,
            confirmCaptureAction = container.confirmCaptureAction,
            brainDumpActions = container.brainDumpActions,
            reminderRepository = container.reminderRepository,
            recordAiLearningEvent = container.recordAiLearningEvent,
            proposeLearnedRule = container.proposeLearnedRule,
            captureInbox = container.captureInbox,
            suggestionDao = container.database.captureSuggestionDao(),
            captureResolution = container.captureResolution,
            savedStateHandle = savedStateHandle,
            applicationContext = container.applicationContext,
            captureAnalyzer = container.captureAnalyzer,
            thoughtSplitter = container.thoughtSplitter,
            reminderSaveOutcomes = container.reminderSaveOutcomes,
        )

        private const val ActiveBrainDumpCaptureIdKey = "activeBrainDumpCaptureId"
        const val OpenCaptureIdKey = "openSortCaptureId"
    }

    private fun localized(@StringRes resId: Int, vararg formatArgs: Any): String =
        localizedContext.getString(resId, *formatArgs)
}

