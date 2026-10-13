package com.orbit.app.ui.screens.review

import com.orbit.app.domain.capture.taskStatus
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.worst
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.ai.AiRouteSource
import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.domain.ai.SourceLinkedAnswer
import com.orbit.app.domain.analyzer.LocalReviewAnalyzer
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.domain.analyzer.TinyActionSuggestion
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.screens.item.ItemSchedule
import com.orbit.app.ui.screens.item.ItemScheduleActions
import com.orbit.app.ui.time.MinuteClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReviewItemType { Task, Capture, Reminder }

enum class ReviewItemSchedule { Timed, DateOnly, None }

internal fun TaskEntity.isDueOn(
    day: LocalDate,
    startOfDay: Long,
    startOfNextDay: Long,
): Boolean = dueAt in startOfDay until startOfNextDay || scheduledDateEpochDay == day.toEpochDay()

internal fun TaskEntity.isOverdueBefore(day: LocalDate, startOfDay: Long): Boolean =
    dueAt != null && dueAt < startOfDay ||
        scheduledDateEpochDay?.let { it < day.toEpochDay() } == true

enum class ReviewSupportingText {
    Task,
    WaitingFor,
    Someday,
    UnfinishedBrainDump,
    UnfinalizedInboxCapture,
    Reminder,
}

enum class ReviewReason { UnfinalizedCapture }

enum class CarryForwardGuidance {
    ChooseNewDayOrSmallerStep,
    RescheduleIfRelevant,
}

enum class ReviewSuggestionSource { Gemini, Local, LocalFallback }

data class ReviewSuggestion(
    val sourceKey: String,
    val sourceTitle: String,
    val action: String,
    val source: ReviewSuggestionSource,
)

data class ReviewItem(
    val id: Long,
    val type: ReviewItemType,
    val title: String,
    val timestamp: Long,
    val schedule: ReviewItemSchedule = ReviewItemSchedule.None,
    val supportingText: ReviewSupportingText? = null,
    val reviewReason: ReviewReason? = null,
    val hasPendingBrainDump: Boolean = false,
) {
    val key: String = "${type.name}_$id"
}

data class CarryForwardSuggestion(
    val item: ReviewItem,
    val guidance: CarryForwardGuidance,
)

data class ReviewUiState(
    /** The time this state was built for; follows the minute clock (F12, N005). */
    val now: Long = 0L,
    val dueToday: List<ReviewItem> = emptyList(),
    /** Open tasks and reminders in the seven days after today ("Coming up"). */
    val nextSevenDays: Int = 0,
    val recentInboxCaptures: List<ReviewItem> = emptyList(),
    val morningSuggestion: ReviewSuggestion? = null,
    val unresolvedCaptures: List<ReviewItem> = emptyList(),
    val completedToday: List<ReviewItem> = emptyList(),
    val carryForwardSuggestions: List<CarryForwardSuggestion> = emptyList(),
    val openLoops: List<ReviewLoop> = emptyList(),
    val staleLoops: List<ReviewLoop> = emptyList(),
    val waitingFor: List<ReviewItem> = emptyList(),
    val someday: List<ReviewItem> = emptyList(),
    val smallerAction: ReviewSuggestion? = null,
    val weeklySummary: SourceLinkedAnswer? = null,
    val pendingTaskUndo: ReviewTaskUndoToken? = null,
    val staleLoopDays: Int = LocalReviewAnalyzer.DefaultStaleLoopDays,
    /** Every unresolved thought with LUMA's stored suggestion. */
    val toSort: List<ToSortItem> = emptyList(),
    val pendingSortUndo: SortUndoToken? = null,
    val sortMessage: ReviewSortMessage? = null,
    /** Undo for the latest "To tomorrow", carry-forward or thought action. */
    val pendingChangeUndo: ReviewChangeToken? = null,
    /** True while the weekly look back is being written. */
    val weeklySummaryLoading: Boolean = false,
    /** Thoughts with one real question (stored outcome `NeedsYou`), newest first. */
    val needsYou: List<NeedsYouItem> = emptyList(),
    /** Thoughts saved today that Tallele kept as they are. */
    val keptToday: Int = 0,
    /** Thoughts saved today that Tallele filed (a note, task or reminder). */
    val filedToday: Int = 0,
) {
    /** Nothing needs a decision, nothing is due today and no kept thought waits in To sort. */
    val nothingWaiting: Boolean
        get() = needsYou.isEmpty() && dueToday.isEmpty() && toSort.isEmpty()

    /** The tab shows a dot only when something failed, never a count. */
    val somethingFailed: Boolean
        get() = needsYou.any { it.somethingFailed }
}

/** An undoable outcome from To sort. */
sealed interface SortUndoToken {
    val captureId: Long

    data class Accepted(
        override val captureId: Long,
        val itemType: com.orbit.app.data.local.entity.SuggestedItemType,
        val itemId: Long,
        /** For a reminder: whether it can actually reach the user. */
        val reminderOutcome: ReminderSaveOutcome? = null,
    ) : SortUndoToken
    data class LetGo(override val captureId: Long) : SortUndoToken
    data class Hidden(override val captureId: Long) : SortUndoToken

    /** "Accept all suggestions": one Undo puts every thought back in To sort. */
    data class AcceptedMany(val accepted: List<Accepted>) : SortUndoToken {
        override val captureId: Long get() = accepted.first().captureId
    }
}

/** The least happy reminder outcome in this feedback, if it saved any reminder. */
val SortUndoToken.reminderOutcome: ReminderSaveOutcome?
    get() = when (this) {
        is SortUndoToken.Accepted -> reminderOutcome
        is SortUndoToken.AcceptedMany -> accepted.mapNotNull { it.reminderOutcome }.worst()
        else -> null
}

enum class ReviewSortMessage {
    ActionFailed, NeedsChoice, Undone, TimePassed, Kept, ReminderSet, TaskMade, ReminderRearmed,
    NoteSaved, Filed, ReminderNotificationsOff, ReminderNotScheduled,
}

internal data class ReviewData(
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val spaces: List<com.orbit.app.data.local.entity.SpaceEntity>,
    val settings: AppSettings,
    val brainDumpCaptureIds: Set<Long>,
    val suggestions: Map<Long, com.orbit.app.data.local.entity.CaptureSuggestionEntity> = emptyMap(),
    val brainDumpPending: Map<Long, Int> = emptyMap(),
)

class ReviewViewModel internal constructor(
    private val container: OrbitContainer,
    private val analyzer: LocalReviewAnalyzer = LocalReviewAnalyzer(),
    private val actions: ReviewActions = ReviewActions(
        captureRepository = container.captureRepository,
        taskRepository = container.taskRepository,
        reminderRepository = container.reminderRepository,
        confirmCaptureAction = container.confirmCaptureAction,
        scheduleActions = ItemScheduleActions(
            noteRepository = container.noteRepository,
            taskRepository = container.taskRepository,
        ),
        brainDumpActions = container.brainDumpActions,
        archivedTaskStatus = container.archivedTaskStatusMemory,
    ),
    private val taskUndoController: ReviewTaskUndoController = ReviewTaskUndoController(
        container.taskRepository,
    ),
) : ViewModel() {
    private val smallerAction = MutableStateFlow<ReviewSuggestion?>(null)
    private val weeklySummary = MutableStateFlow<SourceLinkedAnswer?>(null)
    private val pendingTaskUndo = MutableStateFlow<ReviewTaskUndoToken?>(null)
    private val sortFeedback = MutableStateFlow<Pair<SortUndoToken?, ReviewSortMessage?>>(null to null)
    private val pendingChange = MutableStateFlow<ReviewChangeToken?>(null)
    private val weeklyLoading = MutableStateFlow(false)
    private val changeUndo = ReviewChangeUndo(
        taskRepository = container.taskRepository,
        reminderRepository = container.reminderRepository,
        captureRepository = container.captureRepository,
        brainDumpRepository = container.brainDumpRepository,
    )
    private var sortActionInProgress = false
    private var weeklyDataVersion = 0L

    private val corpus = combine(
        container.captureRepository.observeAll(),
        container.noteRepository.observeAll(),
        container.taskRepository.observeAll(),
        container.reminderRepository.observeAll(),
        container.spaceRepository.observeAll(),
    ) { captures, notes, tasks, reminders, spaces ->
        SearchCorpus(
            captures = captures,
            notes = notes,
            tasks = tasks,
            reminders = reminders,
            spaces = spaces,
        )
    }

    private val reviewData = combine(
        corpus,
        container.appSettingsRepository.settings,
        container.brainDumpRepository.observeSessions(),
        container.database.captureSuggestionDao().observeAll(),
        container.database.brainDumpDao().observePendingCounts(),
    ) { corpus, settings, sessions, suggestions, pendingCounts ->
        ReviewData(
            suggestions = suggestions.associateBy { it.captureId },
            brainDumpPending = pendingCounts.associate { it.captureId to it.pending },
            captures = corpus.captures,
            notes = corpus.notes,
            tasks = corpus.tasks,
            reminders = corpus.reminders,
            spaces = corpus.spaces,
            settings = settings,
            brainDumpCaptureIds = sessions.mapTo(hashSetOf()) { it.captureId },
        )
    }

    init {
        viewModelScope.launch {
            reviewData.collect {
                weeklyDataVersion += 1
                weeklySummary.value = null
                // B10: the look back on screen is made again for the new data, not left empty.
                if (weeklyWanted) loadWeeklySummary()
            }
        }
    }

    val uiState = combine(
        combine(reviewData, MinuteClock.ticks()) { data, now -> data to now },
        smallerAction,
        weeklySummary,
        pendingTaskUndo,
        combine(sortFeedback, pendingChange, weeklyLoading) { sort, change, loading -> Triple(sort, change, loading) },
    ) { (data, now), smallAction, summary, taskUndo, (feedback, change, loading) ->
        val state = buildUiState(data, smallAction, summary, taskUndo, now)
        state.copy(
            pendingSortUndo = feedback.first,
            sortMessage = feedback.second,
            pendingChangeUndo = change,
            weeklySummaryLoading = loading && summary == null,
            toSort = state.toSort.map { item -> item.withSplitOffer() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(),
    )

    private val splitOffers = mutableMapOf<String, Int>()

    /** "Looks like N thoughts" for one-line thoughts; remembered per text, it never changes. */
    private suspend fun ToSortItem.withSplitOffer(): ToSortItem {
        if (state != ToSortState.Suggested && state != ToSortState.NoSuggestion) return this
        if (text.lines().count(String::isNotBlank) > 1) return this
        val count = splitOffers[text] ?: runCatching { container.thoughtSplitter.possibleThoughts(text) }
            .getOrDefault(0)
            .also { splitOffers[text] = it }
        return if (count >= 2) copy(possibleThoughts = count) else this
    }

    /** Needs you: a reminder at a quick time (today 18:00, tomorrow 09:00) for a thought without one. */
    fun remindAt(item: NeedsYouItem, reminderAt: Long) {
        // N005: a quick time that passed while the card was open is refused, not saved as missed.
        if (com.orbit.app.reminders.ReminderTimeCheck.problem(reminderAt, 0L, System.currentTimeMillis()) != null) {
            sortFeedback.value = null to ReviewSortMessage.TimePassed
            return
        }
        answer(item) {
            val outcome = container.captureResolution.createQuickReminder(item.captureId, item.title, reminderAt)
            // Only a reminder that can ring is "set"; otherwise the card says why.
            ReviewSortMessage.ReminderSet.takeIf { outcome == com.orbit.app.reminders.ReminderSaveOutcome.Saved }
        }
    }

    /** Needs you: "Just keep it". A reminder made for the thought is deleted and its alarm cancelled. */
    fun keepThought(item: NeedsYouItem) = answer(item) {
        container.captureResolution.keep(item.captureId)
        ReviewSortMessage.Kept
    }

    /** Needs you: "Make it a task" for a thought Tallele could not place. */
    fun makeTask(item: NeedsYouItem) = answer(item) {
        container.confirmCaptureAction.createTask(
            captureId = item.captureId,
            spaceId = item.suggestedSpaceName?.let { name ->
                container.spaceRepository.observeAll().first()
                    .firstOrNull { !it.archived && it.name.equals(name, ignoreCase = true) }?.id
            },
            title = item.title,
            dueAt = null,
            scheduledDateEpochDay = item.taskDateEpochDay,
            status = container.database.captureSuggestionDao().getByCaptureId(item.captureId)
                ?.taskStatus()
                ?: TaskStatus.Open,
        )
        ReviewSortMessage.TaskMade
    }

    /** Needs you: "Try again" for a reminder whose alarm could not be set. */
    fun retryReminder(item: NeedsYouItem) {
        val reminderId = item.reminderId ?: return
        answer(item) {
            com.orbit.app.ui.screens.home.rearmReceiptReminder(
                reminderRepository = container.reminderRepository,
                outcomes = container.reminderSaveOutcomes,
                resolution = container.captureResolution,
                captureId = item.captureId,
                reminderId = reminderId,
            )
            ReviewSortMessage.ReminderRearmed.takeIf {
                container.reminderSaveOutcomes.of(reminderId) == com.orbit.app.reminders.ReminderSaveOutcome.Saved
            }
        }
    }

    /** Cards being answered: a second tap on the same card waits; other cards stay usable. */
    private val answering = mutableSetOf<Long>()

    private fun answer(item: NeedsYouItem, action: suspend () -> ReviewSortMessage?) {
        if (!answering.add(item.captureId)) return
        viewModelScope.launch {
            try {
                // N025: an answered card says what happened.
                action()?.let { done -> sortFeedback.value = null to done }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                sortFeedback.value = null to ReviewSortMessage.ActionFailed
            } finally {
                answering.remove(item.captureId)
            }
        }
    }

    /** One tap: turn the thought into what LUMA suggested. */
    fun acceptSuggestion(item: ToSortItem) {
        runSortAction {
            runCatching { container.captureResolution.acceptSuggestion(item.captureId) }
                .onSuccess { accepted ->
                    sortFeedback.value = if (accepted == null) {
                        null to ReviewSortMessage.NeedsChoice
                    } else {
                        SortUndoToken.Accepted(item.captureId, accepted.itemType, accepted.itemId, accepted.reminderOutcome) to null
                    }
                }
                .onFailure { sortFeedback.value = null to ReviewSortMessage.ActionFailed }
        }
    }

    /**
     * Accepts every waiting suggestion that needs no further choice. Thoughts that need a
     * time, or have no suggestion, stay in To sort. One Undo reverts them all.
     */
    fun acceptAllSuggestions(items: List<ToSortItem>) {
        val ready = items.filter { it.state == ToSortState.Suggested }
        if (ready.isEmpty()) return
        runSortAction {
            val accepted = mutableListOf<SortUndoToken.Accepted>()
            var failed = false
            ready.forEach { item ->
                runCatching { container.captureResolution.acceptSuggestion(item.captureId) }
                    .onSuccess { result ->
                        result?.let {
                            accepted += SortUndoToken.Accepted(item.captureId, it.itemType, it.itemId, it.reminderOutcome)
                        }
                    }
                    .onFailure { failed = true }
            }
            sortFeedback.value = when {
                accepted.isNotEmpty() -> SortUndoToken.AcceptedMany(accepted) to null
                failed -> null to ReviewSortMessage.ActionFailed
                else -> null to ReviewSortMessage.NeedsChoice
            }
        }
    }

    /** Hides LUMA's suggestion; the thought stays in To sort. */
    fun hideSuggestion(item: ToSortItem) {
        viewModelScope.launch {
            runCatching { container.captureInbox.dismissSuggestion(item.captureId) }
                .onSuccess { sortFeedback.value = SortUndoToken.Hidden(item.captureId) to null }
                .onFailure { sortFeedback.value = null to ReviewSortMessage.ActionFailed }
        }
    }

    /** The user explicitly lets a thought go (archived, with Undo). */
    fun letGo(item: ToSortItem) {
        viewModelScope.launch {
            runCatching { container.captureResolution.archive(item.captureId) }
                .onSuccess { sortFeedback.value = SortUndoToken.LetGo(item.captureId) to null }
                .onFailure { sortFeedback.value = null to ReviewSortMessage.ActionFailed }
        }
    }

    fun undoSort(token: SortUndoToken) {
        viewModelScope.launch {
            runCatching {
                when (token) {
                    is SortUndoToken.Accepted -> container.captureResolution.undo(token.captureId, token.itemType, token.itemId)
                    is SortUndoToken.LetGo -> container.captureResolution.unarchive(token.captureId)
                    is SortUndoToken.Hidden -> container.captureInbox.restoreSuggestion(token.captureId)
                    is SortUndoToken.AcceptedMany -> token.accepted.forEach {
                        container.captureResolution.undo(it.captureId, it.itemType, it.itemId)
                    }
                }
            }
                .onSuccess { sortFeedback.value = null to ReviewSortMessage.Undone }
                .onFailure { sortFeedback.value = null to ReviewSortMessage.ActionFailed }
        }
    }

    /**
     * Clears the feedback the screen just finished showing. It only clears that exact
     * feedback, so a newer result (for example "Undone") that arrived meanwhile is
     * still shown.
     */
    /** A message from the one-item sheet (review §9.3), shown like any other sort message. */
    fun showSortMessage(message: ReviewSortMessage) {
        sortFeedback.value = null to message
    }

    fun sortFeedbackShown(token: SortUndoToken?, message: ReviewSortMessage?) {
        sortFeedback.compareAndSet(token to message, null to null)
    }

    /**
     * Runs one accept action at a time. A second tap while the first is still saving
     * would otherwise fail on the already sorted thought and replace the first
     * action's Undo with an error message.
     */
    private fun runSortAction(action: suspend () -> Unit) {
        if (sortActionInProgress) return
        sortActionInProgress = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                sortActionInProgress = false
            }
        }
    }

    fun keepTaskActive(loop: ReviewLoop) = updateLoop(loop, actions::keepTaskActive)

    fun confirmCapture(loop: ReviewLoop) = changeThought(loop, ReviewChangeKind.ThoughtKept, actions::confirmCapture)

    fun archive(loop: ReviewLoop) {
        if (loop.type == ReviewLoopType.Task) {
            updateUndoableTask(loop, actions::archive)
        } else {
            changeThought(loop, ReviewChangeKind.ThoughtLetGo) { actions.archive(it) }
        }
    }

    fun completeTask(loop: ReviewLoop) = updateUndoableTask(loop, actions::completeTask)

    fun deferTask(loop: ReviewLoop) = updateUndoableTask(loop, actions::deferTask)

    fun carryForwardTomorrow(item: ReviewItem) = carryForwardAllTomorrow(listOf(item))

    /** "To tomorrow" for every item from earlier at once, with one Undo. */
    fun carryForwardAllTomorrow(items: List<ReviewItem>) {
        val movable = items.filter { it.type != ReviewItemType.Capture }
        if (movable.isEmpty()) return
        changeItems(movable, ReviewChangeKind.MovedTomorrow) { movable.forEach { actions.carryForwardTomorrow(it) } }
    }

    /** A chosen day; days before today are refused (the picker does not offer them either). */
    fun carryForwardToDate(item: ReviewItem, epochDay: Long) {
        if (!carryForwardDayAllowed(epochDay, LocalDate.now(ZoneId.systemDefault()))) return
        changeItems(listOf(item), ReviewChangeKind.MovedToDate) { actions.carryForwardToDate(item, epochDay) }
    }

    fun keepCarryForwardUnscheduled(item: ReviewItem) =
        changeItems(listOf(item), ReviewChangeKind.KeptUndated) { actions.keepUnscheduled(item) }

    fun completeCarryForward(item: ReviewItem) =
        changeItems(listOf(item), ReviewChangeKind.Completed) { actions.completeCarryForward(item) }

    fun dismissCapture(loop: ReviewLoop) = changeThought(loop, ReviewChangeKind.ThoughtHandled, actions::dismissCapture)

    fun undoChange(operationId: Long) {
        viewModelScope.launch {
            if (pendingChange.value?.operationId != operationId) return@launch
            runCatching { changeUndo.undo(operationId) }
            if (pendingChange.value?.operationId == operationId) pendingChange.value = null
        }
    }

    /** Clears [operationId] only if it is still the latest change, so a newer Undo is kept. */
    fun changeUndoExpired(operationId: Long) {
        if (pendingChange.value?.operationId == operationId) {
            changeUndo.expire(operationId)
            pendingChange.value = null
        }
    }

    private fun changeItems(items: List<ReviewItem>, kind: ReviewChangeKind, change: suspend () -> Unit) {
        val targets = ReviewChangeTargets(
            taskIds = items.filter { it.type == ReviewItemType.Task }.map { it.id },
            reminderIds = items.filter { it.type == ReviewItemType.Reminder }.map { it.id },
        )
        viewModelScope.launch {
            runCatching { changeUndo.record(kind, targets, change) }
                .onSuccess { pendingChange.value = it }
        }
    }

    private fun changeThought(loop: ReviewLoop, kind: ReviewChangeKind, change: suspend (ReviewLoop) -> Unit) {
        viewModelScope.launch {
            runCatching {
                changeUndo.record(kind, ReviewChangeTargets(captureIds = listOf(loop.id))) { change(loop) }
            }.onSuccess { pendingChange.value = it }
            if (smallerAction.value?.sourceKey == loop.key) smallerAction.value = null
        }
    }

    // The look back was opened, so it should show something for the current data.
    private var weeklyWanted = false

    fun loadWeeklySummary() {
        weeklyWanted = true
        if (weeklySummary.value != null || weeklyLoading.value) return
        weeklyLoading.value = true
        viewModelScope.launch {
            var outdated = false
            try {
                val data = reviewData.first()
                val version = weeklyDataVersion
                val sources = weeklyReviewSources(data, System.currentTimeMillis(), ZoneId.systemDefault())
                val summary = container.aiRouter.summarizeReview(sources, data.settings)
                if (version == weeklyDataVersion) weeklySummary.value = summary else outdated = true
            } finally {
                // A failure leaves the calm fallback text, never an endless spinner.
                weeklyLoading.value = false
            }
            // B10: the data changed while it was being made: make it once more.
            if (outdated) loadWeeklySummary()
        }
    }

    fun makeSmaller(loop: ReviewLoop) {
        viewModelScope.launch {
            val settings = container.appSettingsRepository.settings.first()
            val routedAction = container.aiRouter.makeSmaller(loop.title, settings)
            smallerAction.value = ReviewSuggestion(
                sourceKey = loop.key,
                sourceTitle = loop.title,
                action = routedAction.action,
                source = routedAction.metadata.source.reviewSuggestionSource(),
            )
        }
    }

    fun undoTaskMutation(operationId: Long) {
        viewModelScope.launch {
            if (pendingTaskUndo.value?.operationId != operationId) return@launch
            taskUndoController.undo(operationId)
            if (pendingTaskUndo.value?.operationId == operationId) {
                pendingTaskUndo.value = null
            }
        }
    }

    fun expireTaskUndo(operationId: Long) {
        if (pendingTaskUndo.value?.operationId == operationId) {
            taskUndoController.expire(operationId)
            pendingTaskUndo.value = null
        }
    }

    private fun updateLoop(loop: ReviewLoop, update: suspend (ReviewLoop) -> Unit) {
        viewModelScope.launch {
            update(loop)
            if (smallerAction.value?.sourceKey == loop.key) smallerAction.value = null
        }
    }

    private fun updateUndoableTask(
        loop: ReviewLoop,
        update: suspend (ReviewLoop) -> ReviewTaskMutation?,
    ) {
        viewModelScope.launch {
            update(loop)?.let { mutation ->
                pendingTaskUndo.value = taskUndoController.record(mutation)
            }
            if (smallerAction.value?.sourceKey == loop.key) smallerAction.value = null
        }
    }

    private fun buildUiState(
        data: ReviewData,
        smallAction: ReviewSuggestion?,
        summary: SourceLinkedAnswer?,
        taskUndo: ReviewTaskUndoToken?,
        now: Long = System.currentTimeMillis(),
    ): ReviewUiState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val savedToday = savedTodayCounts(data.captures, startOfToday, startOfTomorrow)
        val recentCutoff = Instant.ofEpochMilli(startOfToday)
            .minus(7, ChronoUnit.DAYS)
            .toEpochMilli()

        val activeTasks = data.tasks.filter { it.status == TaskStatus.Open }
        val somedayTasks = data.tasks.filter { it.status == TaskStatus.Someday }
        val inboxCaptures = data.captures.filter(::isUnresolvedReviewCapture)
        val dueTasks = activeTasks
            .filter { it.isDueOn(today, startOfToday, startOfTomorrow) }
            .map { it.asReviewItem(timestamp = it.dueAt ?: startOfToday) }
        val dueReminders = data.reminders
            .filter { it.completedAt == null && it.dueAt in startOfToday until startOfTomorrow }
            .map { it.asReviewItem() }
        val dueToday = (dueTasks + dueReminders).sortedWith(reviewScheduleOrder)
        // Review: "Coming up" looks past today: the next seven days.
        val weekEnd = today.plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextSevenDays = activeTasks.count { task ->
            val day = task.scheduledDateEpochDay
                ?: task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            day != null && day > today.toEpochDay() && day <= today.plusDays(7).toEpochDay()
        } + data.reminders.count { it.completedAt == null && it.dueAt in startOfTomorrow until weekEnd }
        val recentInbox = inboxCaptures
            .filter { it.createdAt >= recentCutoff }
            .sortedByDescending { it.createdAt }
            .take(5)
            .map { it.asReviewItem(data.brainDumpCaptureIds) }
        val morningSource = dueToday.firstOrNull()?.asReviewLoop()
            ?: recentInbox.firstOrNull()?.asReviewLoop()

        val completedTasks = data.tasks
            .filter {
                val completedAt = it.completedAt
                completedAt != null && completedAt in startOfToday until startOfTomorrow
            }
            .map { it.asReviewItem(timestamp = it.completedAt ?: it.updatedAt) }
        val completedReminders = data.reminders
            .filter {
                val completedAt = it.completedAt
                completedAt != null && completedAt in startOfToday until startOfTomorrow
            }
            .map { it.asReviewItem(timestamp = it.completedAt ?: it.updatedAt) }
        val overdueTasks = activeTasks
            .filter { it.isOverdueBefore(today, startOfToday) }
            .map {
                CarryForwardSuggestion(
                    item = it.asReviewItem(
                        timestamp = it.dueAt ?: LocalDate.ofEpochDay(it.scheduledDateEpochDay!!)
                            .atStartOfDay(zone).toInstant().toEpochMilli(),
                    ),
                    guidance = CarryForwardGuidance.ChooseNewDayOrSmallerStep,
                )
            }
        val overdueReminders = data.reminders
            .filter { it.completedAt == null && it.dueAt < startOfToday }
            .map {
                CarryForwardSuggestion(
                    item = it.asReviewItem(),
                    guidance = CarryForwardGuidance.RescheduleIfRelevant,
                )
            }

        // Unresolved captures are handled in To sort, so open loops list tasks only.
        val openLoops = activeTasks
            .map { ReviewLoop(it.id, ReviewLoopType.Task, it.title, it.updatedAt) }
            .sortedBy { it.updatedAt }

        return ReviewUiState(
            now = now,
            dueToday = dueToday,
            nextSevenDays = nextSevenDays,
            recentInboxCaptures = recentInbox,
            morningSuggestion = morningSource
                ?.let(analyzer::makeSmaller)
                ?.asReviewSuggestion(ReviewSuggestionSource.Local),
            unresolvedCaptures = inboxCaptures
                .sortedByDescending { it.updatedAt }
                .map { it.asReviewItem(data.brainDumpCaptureIds) },
            completedToday = (completedTasks + completedReminders)
                .sortedByDescending { it.timestamp },
            carryForwardSuggestions = (overdueTasks + overdueReminders)
                .sortedWith(compareBy<CarryForwardSuggestion> { if (it.item.schedule == ReviewItemSchedule.DateOnly) 0 else 1 }
                    .thenBy { it.item.timestamp })
                .take(5),
            openLoops = openLoops,
            staleLoops = analyzer.findStaleLoops(
                tasks = data.tasks,
                captures = data.captures,
                staleLoopDays = data.settings.staleLoopDays,
                now = now,
            ).map { loop ->
                if (loop.type == ReviewLoopType.Capture) {
                    loop.copy(hasPendingBrainDump = loop.id in data.brainDumpCaptureIds)
                } else {
                    loop
                }
            },
            waitingFor = data.tasks
                .filter { it.status == TaskStatus.WaitingFor }
                .sortedBy { it.updatedAt }
                .map { it.asReviewItem() },
            someday = somedayTasks
                .sortedByDescending { it.updatedAt }
                .take(3)
                .map { it.asReviewItem() },
            smallerAction = smallAction,
            weeklySummary = summary,
            pendingTaskUndo = taskUndo,
            staleLoopDays = data.settings.staleLoopDays,
            toSort = buildToSort(
                captures = data.captures,
                suggestions = data.suggestions,
                brainDumpPending = data.brainDumpPending,
                brainDumpCaptureIds = data.brainDumpCaptureIds,
                now = now,
            ),
            needsYou = buildNeedsYou(data.captures, data.suggestions, data.reminders),
            keptToday = savedToday.kept,
            filedToday = savedToday.filed,
        )
    }

    private fun TaskEntity.asReviewItem(timestamp: Long = dueAt ?: updatedAt) = ReviewItem(
        id = id,
        type = ReviewItemType.Task,
        title = title,
        timestamp = timestamp,
        schedule = when {
            scheduledDateEpochDay != null -> ReviewItemSchedule.DateOnly
            dueAt != null -> ReviewItemSchedule.Timed
            else -> ReviewItemSchedule.None
        },
        supportingText = when (status) {
            TaskStatus.WaitingFor -> ReviewSupportingText.WaitingFor
            TaskStatus.Someday -> ReviewSupportingText.Someday
            else -> ReviewSupportingText.Task
        },
    )

    private fun CaptureEntity.asReviewItem(brainDumpCaptureIds: Set<Long>) = ReviewItem(
        id = id,
        type = ReviewItemType.Capture,
        title = rawText,
        timestamp = updatedAt,
        supportingText = if (id in brainDumpCaptureIds) {
            ReviewSupportingText.UnfinishedBrainDump
        } else {
            ReviewSupportingText.UnfinalizedInboxCapture
        },
        reviewReason = reviewReason(),
        hasPendingBrainDump = id in brainDumpCaptureIds,
    )

    private fun ReminderEntity.asReviewItem(timestamp: Long = dueAt) = ReviewItem(
        id = id,
        type = ReviewItemType.Reminder,
        title = title,
        timestamp = timestamp,
        schedule = ReviewItemSchedule.Timed,
        supportingText = ReviewSupportingText.Reminder,
    )

    private fun ReviewItem.asReviewLoop(): ReviewLoop = ReviewLoop(
        id = id,
        type = if (type == ReviewItemType.Capture) ReviewLoopType.Capture else ReviewLoopType.Task,
        title = title,
        updatedAt = timestamp,
        hasPendingBrainDump = hasPendingBrainDump,
    )

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReviewViewModel::class.java))
            return ReviewViewModel(container) as T
        }
    }
}

private val reviewScheduleOrder = compareBy<ReviewItem> {
    if (it.schedule == ReviewItemSchedule.DateOnly) 0 else 1
}.thenBy { it.timestamp }

internal fun weeklyReviewSources(
    data: ReviewData,
    now: Long,
    zoneId: ZoneId,
): List<AiSourceItem> {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zoneId).toInstant().toEpochMilli()
    fun inCurrentWeek(timestamp: Long?) = timestamp != null && timestamp in weekStart..now
    val spacesById = data.spaces.associateBy { it.id }
    return buildList {
        data.captures
            .filter { it.status == CaptureStatus.Inbox && (inCurrentWeek(it.createdAt) || inCurrentWeek(it.updatedAt)) }
            .forEach {
                add(AiSourceItem("capture:${it.id}", ItemDetailType.Capture, it.id, it.rawText, it.rawText, it.suggestedSpaceId?.let(spacesById::get)?.name, it.status.name, it.updatedAt, it.createdAt))
            }
        data.notes
            .filterNot { it.archived }
            .filter { inCurrentWeek(it.createdAt) || inCurrentWeek(it.updatedAt) }
            .forEach {
                add(AiSourceItem("note:${it.id}", ItemDetailType.Note, it.id, it.title, it.body.ifBlank { it.title }, it.spaceId?.let(spacesById::get)?.name, "Note", it.updatedAt, it.createdAt))
            }
        data.tasks
            .filterNot { it.status == TaskStatus.Archived }
            .filter { inCurrentWeek(it.createdAt) || inCurrentWeek(it.updatedAt) || inCurrentWeek(it.completedAt) }
            .forEach {
                add(AiSourceItem("task:${it.id}", ItemDetailType.Task, it.id, it.title, it.notes.ifBlank { it.status.name }, it.spaceId?.let(spacesById::get)?.name, it.status.name, it.completedAt ?: it.updatedAt, it.createdAt, it.dueAt, it.scheduledDateEpochDay))
            }
        data.reminders
            .filter { inCurrentWeek(it.createdAt) || inCurrentWeek(it.updatedAt) || inCurrentWeek(it.completedAt) }
            .forEach {
                add(AiSourceItem("reminder:${it.id}", ItemDetailType.Reminder, it.id, it.title, it.notes, it.spaceId?.let(spacesById::get)?.name, if (it.completedAt == null) "Reminder" else "Completed", it.completedAt ?: it.updatedAt, it.createdAt, it.dueAt))
            }
    }.sortedByDescending { it.timestamp }.take(10)
}

private fun AiRouteSource.reviewSuggestionSource(): ReviewSuggestionSource = when (this) {
    AiRouteSource.Gemini -> ReviewSuggestionSource.Gemini
    AiRouteSource.Local -> ReviewSuggestionSource.Local
    AiRouteSource.GeminiFailedLocalUsed -> ReviewSuggestionSource.LocalFallback
}

private fun TinyActionSuggestion.asReviewSuggestion(
    source: ReviewSuggestionSource,
): ReviewSuggestion = ReviewSuggestion(
    sourceKey = sourceKey,
    sourceTitle = sourceTitle,
    action = action,
    source = source,
)

internal fun isUnresolvedReviewCapture(capture: CaptureEntity): Boolean =
    capture.status == CaptureStatus.Inbox

internal fun CaptureEntity.reviewReason(): ReviewReason = ReviewReason.UnfinalizedCapture

enum class ReviewTaskMutationAction {
    Completed,
    Deferred,
    Archived,
}

internal data class ReviewTaskMutation(
    val action: ReviewTaskMutationAction,
    val original: TaskEntity,
    val updated: TaskEntity,
)

internal class ReviewActions(
    private val captureRepository: CaptureRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val scheduleActions: ItemScheduleActions,
    private val brainDumpActions: BrainDumpActions? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zoneId) },
    private val archivedTaskStatus: com.orbit.app.data.local.ArchivedTaskStatusMemory =
        com.orbit.app.data.local.ArchivedTaskStatusMemory.None,
) {
    suspend fun keepTaskActive(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Task)
        taskRepository.getById(loop.id)?.let {
            taskRepository.update(it.copy(updatedAt = now()))
        }
    }

    suspend fun confirmCapture(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Capture)
        val capture = captureRepository.getById(loop.id) ?: return
        try {
            confirmCaptureAction.createTask(
                captureId = capture.id,
                spaceId = capture.suggestedSpaceId,
                title = capture.rawText,
                dueAt = null,
                status = TaskStatus.Someday,
            )
        } catch (exception: IllegalStateException) {
            val latest = captureRepository.getById(capture.id)
            if (latest?.status != CaptureStatus.Processed || latest.linkedItemId == null) {
                throw exception
            }
        }
    }

    suspend fun archive(loop: ReviewLoop): ReviewTaskMutation? = when (loop.type) {
        ReviewLoopType.Task -> taskRepository.getById(loop.id)?.let { task ->
            val updated = task.copy(status = TaskStatus.Archived, updatedAt = now())
            archivedTaskStatus.remember(task.id, task.status)
            taskRepository.update(updated)
            ReviewTaskMutation(
                action = ReviewTaskMutationAction.Archived,
                original = task,
                updated = updated,
            )
        }

        ReviewLoopType.Capture -> {
            captureRepository.getById(loop.id)?.let { capture ->
                if (brainDumpActions != null) {
                    brainDumpActions.dismissCapture(loop.id, archive = true)
                } else {
                    captureRepository.update(
                        capture.copy(status = CaptureStatus.Archived, updatedAt = now()),
                    )
                }
            }
            null
        }
    }

    suspend fun completeTask(loop: ReviewLoop): ReviewTaskMutation? {
        require(loop.type == ReviewLoopType.Task)
        val task = taskRepository.getById(loop.id) ?: return null
        val completedAt = now()
        val updated = task.copy(
            status = TaskStatus.Done,
            updatedAt = completedAt,
            completedAt = completedAt,
        )
        taskRepository.update(updated)
        return ReviewTaskMutation(
            action = ReviewTaskMutationAction.Completed,
            original = task,
            updated = updated,
        )
    }

    suspend fun carryForwardTomorrow(item: ReviewItem) {
        carryForwardToDate(item, today().plusDays(1).toEpochDay())
    }

    suspend fun carryForwardToDate(item: ReviewItem, epochDay: Long) {
        val date = LocalDate.ofEpochDay(epochDay)
        when (item.type) {
            ReviewItemType.Task -> scheduleActions.apply(
                type = ItemDetailType.Task,
                itemId = item.id,
                schedule = ItemSchedule.DateOnly(epochDay),
            )

            ReviewItemType.Reminder -> reminderRepository.getById(item.id)?.let { reminder ->
                val localTime = Instant.ofEpochMilli(reminder.dueAt).atZone(zoneId).toLocalTime()
                reminderRepository.update(
                    reminder.copy(
                        dueAt = date.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli(),
                        completedAt = null,
                        updatedAt = now(),
                    ),
                )
            }

            ReviewItemType.Capture -> error("Captures cannot be carried forward")
        }
    }

    suspend fun keepUnscheduled(item: ReviewItem) {
        require(item.type == ReviewItemType.Task)
        scheduleActions.apply(
            type = ItemDetailType.Task,
            itemId = item.id,
            schedule = ItemSchedule.Unscheduled,
        )
    }

    suspend fun completeCarryForward(item: ReviewItem) {
        when (item.type) {
            ReviewItemType.Task -> completeTask(item.id)
            ReviewItemType.Reminder -> reminderRepository.getById(item.id)?.let { reminder ->
                reminderRepository.update(com.orbit.app.reminders.ReminderRepeats.markDone(reminder, now()))
            }

            ReviewItemType.Capture -> error("Captures cannot be completed")
        }
    }

    suspend fun deferTask(loop: ReviewLoop): ReviewTaskMutation? {
        require(loop.type == ReviewLoopType.Task)
        val task = taskRepository.getById(loop.id) ?: return null
        val updated = task.copy(status = TaskStatus.Someday, updatedAt = now(), completedAt = null)
        taskRepository.update(updated)
        return ReviewTaskMutation(
            action = ReviewTaskMutationAction.Deferred,
            original = task,
            updated = updated,
        )
    }

    suspend fun dismissCapture(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Capture)
        if (brainDumpActions != null) {
            brainDumpActions.dismissCapture(loop.id, archive = false)
        } else {
            confirmCaptureAction.markCaptureReviewed(loop.id)
        }
    }

    private suspend fun completeTask(taskId: Long) {
        taskRepository.getById(taskId)?.let {
            val completedAt = now()
            taskRepository.update(
                it.copy(status = TaskStatus.Done, updatedAt = completedAt, completedAt = completedAt),
            )
        }
    }
}
