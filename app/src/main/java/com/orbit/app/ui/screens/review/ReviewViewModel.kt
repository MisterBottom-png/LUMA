package com.orbit.app.ui.screens.review

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
    val dueToday: List<ReviewItem> = emptyList(),
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
    val staleLoopDays: Int = LocalReviewAnalyzer.DefaultStaleLoopDays,
)

internal data class ReviewData(
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val spaces: List<com.orbit.app.data.local.entity.SpaceEntity>,
    val settings: AppSettings,
    val brainDumpCaptureIds: Set<Long>,
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
    ),
) : ViewModel() {
    private val smallerAction = MutableStateFlow<ReviewSuggestion?>(null)
    private val weeklySummary = MutableStateFlow<SourceLinkedAnswer?>(null)
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
    ) { corpus, settings, sessions ->
        ReviewData(
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
            }
        }
    }

    val uiState = combine(reviewData, smallerAction, weeklySummary) { data, smallAction, summary ->
        buildUiState(data, smallAction, summary)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(),
    )

    fun keepTaskActive(loop: ReviewLoop) = updateLoop(loop, actions::keepTaskActive)

    fun confirmCapture(loop: ReviewLoop) = updateLoop(loop, actions::confirmCapture)

    fun archive(loop: ReviewLoop) = updateLoop(loop, actions::archive)

    fun completeTask(loop: ReviewLoop) = updateLoop(loop, actions::completeTask)

    fun deferTask(loop: ReviewLoop) = updateLoop(loop, actions::deferTask)

    fun carryForwardTomorrow(item: ReviewItem) = updateItem(item, actions::carryForwardTomorrow)

    fun carryForwardToDate(item: ReviewItem, epochDay: Long) = updateItem(item) {
        actions.carryForwardToDate(it, epochDay)
    }

    fun keepCarryForwardUnscheduled(item: ReviewItem) =
        updateItem(item, actions::keepUnscheduled)

    fun completeCarryForward(item: ReviewItem) = updateItem(item, actions::completeCarryForward)

    fun dismissCapture(loop: ReviewLoop) = updateLoop(loop, actions::dismissCapture)

    fun loadWeeklySummary() {
        if (weeklySummary.value != null) return
        viewModelScope.launch {
            val data = reviewData.first()
            val version = weeklyDataVersion
            val sources = weeklyReviewSources(data, System.currentTimeMillis(), ZoneId.systemDefault())
            val summary = container.aiRouter.summarizeReview(sources, data.settings)
            if (version == weeklyDataVersion) weeklySummary.value = summary
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

    private fun updateLoop(loop: ReviewLoop, update: suspend (ReviewLoop) -> Unit) {
        viewModelScope.launch {
            update(loop)
            if (smallerAction.value?.sourceKey == loop.key) smallerAction.value = null
        }
    }

    private fun updateItem(item: ReviewItem, update: suspend (ReviewItem) -> Unit) {
        viewModelScope.launch { update(item) }
    }

    private fun buildUiState(
        data: ReviewData,
        smallAction: ReviewSuggestion?,
        summary: SourceLinkedAnswer?,
        now: Long = System.currentTimeMillis(),
    ): ReviewUiState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
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

        val openLoops = (
            activeTasks.map { ReviewLoop(it.id, ReviewLoopType.Task, it.title, it.updatedAt) } +
                inboxCaptures.map {
                    ReviewLoop(
                        it.id,
                        ReviewLoopType.Capture,
                        it.rawText,
                        it.updatedAt,
                        hasPendingBrainDump = it.id in data.brainDumpCaptureIds,
                    )
                }
            ).sortedBy { it.updatedAt }

        return ReviewUiState(
            dueToday = dueToday,
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
            staleLoopDays = data.settings.staleLoopDays,
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
                add(AiSourceItem("task:${it.id}", ItemDetailType.Task, it.id, it.title, it.notes.ifBlank { it.status.name }, it.spaceId?.let(spacesById::get)?.name, it.status.name, it.completedAt ?: it.updatedAt, it.createdAt, it.dueAt))
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

    suspend fun archive(loop: ReviewLoop) {
        when (loop.type) {
            ReviewLoopType.Task -> taskRepository.getById(loop.id)?.let {
                taskRepository.update(
                    it.copy(status = TaskStatus.Archived, updatedAt = now()),
                )
            }

            ReviewLoopType.Capture -> captureRepository.getById(loop.id)?.let {
                if (brainDumpActions != null) {
                    brainDumpActions.dismissCapture(loop.id, archive = true)
                } else {
                    captureRepository.update(
                        it.copy(status = CaptureStatus.Archived, updatedAt = now()),
                    )
                }
            }
        }
    }

    suspend fun completeTask(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Task)
        completeTask(loop.id)
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
                val completedAt = now()
                reminderRepository.update(
                    reminder.copy(completedAt = completedAt, updatedAt = completedAt),
                )
            }

            ReviewItemType.Capture -> error("Captures cannot be completed")
        }
    }

    suspend fun deferTask(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Task)
        taskRepository.getById(loop.id)?.let {
            taskRepository.update(
                it.copy(status = TaskStatus.Someday, updatedAt = now(), completedAt = null),
            )
        }
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
