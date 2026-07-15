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
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.ai.AiRouteSource
import com.orbit.app.domain.ai.LocalAiRetriever
import com.orbit.app.domain.ai.SourceLinkedAnswer
import com.orbit.app.domain.analyzer.LocalReviewAnalyzer
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.domain.analyzer.TinyActionSuggestion
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReviewItemType { Task, Capture, Reminder }

data class ReviewItem(
    val id: Long,
    val type: ReviewItemType,
    val title: String,
    val timestamp: Long,
    val supportingText: String? = null,
    val reviewReason: String? = null,
) {
    val key: String = "${type.name}_$id"
}

data class CarryForwardSuggestion(
    val item: ReviewItem,
    val suggestion: String,
)

data class ReviewUiState(
    val dueToday: List<ReviewItem> = emptyList(),
    val recentInboxCaptures: List<ReviewItem> = emptyList(),
    val morningSuggestion: TinyActionSuggestion? = null,
    val unresolvedCaptures: List<ReviewItem> = emptyList(),
    val completedToday: List<ReviewItem> = emptyList(),
    val carryForwardSuggestions: List<CarryForwardSuggestion> = emptyList(),
    val openLoops: List<ReviewLoop> = emptyList(),
    val staleLoops: List<ReviewLoop> = emptyList(),
    val waitingFor: List<ReviewItem> = emptyList(),
    val someday: List<ReviewItem> = emptyList(),
    val smallerAction: TinyActionSuggestion? = null,
    val weeklySummary: SourceLinkedAnswer? = null,
    val staleLoopDays: Int = LocalReviewAnalyzer.DefaultStaleLoopDays,
)

private data class ReviewData(
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val spaces: List<com.orbit.app.data.local.entity.SpaceEntity>,
    val settings: AppSettings,
)

class ReviewViewModel internal constructor(
    private val container: OrbitContainer,
    private val analyzer: LocalReviewAnalyzer = LocalReviewAnalyzer(),
    private val retriever: LocalAiRetriever = LocalAiRetriever(),
    private val actions: ReviewActions = ReviewActions(
        captureRepository = container.captureRepository,
        taskRepository = container.taskRepository,
        confirmCaptureAction = container.confirmCaptureAction,
    ),
) : ViewModel() {
    private val smallerAction = MutableStateFlow<TinyActionSuggestion?>(null)

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
    ) { corpus, settings ->
        ReviewData(
            captures = corpus.captures,
            notes = corpus.notes,
            tasks = corpus.tasks,
            reminders = corpus.reminders,
            spaces = corpus.spaces,
            settings = settings,
        )
    }

    private val weeklySummary = reviewData.map { data ->
        val sources = retriever.recentContext(data.asCorpus(), limit = 10)
        container.aiRouter.summarizeReview(sources, data.settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

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

    fun dismissCapture(loop: ReviewLoop) = updateLoop(loop, actions::dismissCapture)

    fun makeSmaller(loop: ReviewLoop) {
        viewModelScope.launch {
            val settings = container.appSettingsRepository.settings.first()
            val routedAction = container.aiRouter.makeSmaller(loop.title, settings)
            smallerAction.value = TinyActionSuggestion(
                sourceKey = loop.key,
                sourceTitle = loop.title,
                action = routedAction.action,
                sourceLabel = routedAction.metadata.source.tinyActionLabel(),
            )
        }
    }

    private fun updateLoop(loop: ReviewLoop, update: suspend (ReviewLoop) -> Unit) {
        viewModelScope.launch {
            update(loop)
            if (smallerAction.value?.sourceKey == loop.key) smallerAction.value = null
        }
    }

    private fun buildUiState(
        data: ReviewData,
        smallAction: TinyActionSuggestion?,
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
            .filter { it.dueAt != null && it.dueAt in startOfToday until startOfTomorrow }
            .map { it.asReviewItem() }
        val dueReminders = data.reminders
            .filter { it.completedAt == null && it.dueAt in startOfToday until startOfTomorrow }
            .map { it.asReviewItem() }
        val dueToday = (dueTasks + dueReminders).sortedBy { it.timestamp }
        val recentInbox = inboxCaptures
            .filter { it.createdAt >= recentCutoff }
            .sortedByDescending { it.createdAt }
            .take(5)
            .map { it.asReviewItem() }
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
            .filter { it.dueAt != null && it.dueAt < startOfToday }
            .map {
                CarryForwardSuggestion(
                    item = it.asReviewItem(),
                    suggestion = "Choose a new day, or make the next step smaller.",
                )
            }
        val overdueReminders = data.reminders
            .filter { it.completedAt == null && it.dueAt < startOfToday }
            .map {
                CarryForwardSuggestion(
                    item = it.asReviewItem(),
                    suggestion = "Reschedule when this still matters.",
                )
            }

        val openLoops = (
            activeTasks.map { ReviewLoop(it.id, ReviewLoopType.Task, it.title, it.updatedAt) } +
                inboxCaptures.map {
                    ReviewLoop(it.id, ReviewLoopType.Capture, it.rawText, it.updatedAt)
                }
            ).sortedBy { it.updatedAt }

        return ReviewUiState(
            dueToday = dueToday,
            recentInboxCaptures = recentInbox,
            morningSuggestion = morningSource?.let(analyzer::makeSmaller),
            unresolvedCaptures = inboxCaptures
                .sortedByDescending { it.updatedAt }
                .map { it.asReviewItem() },
            completedToday = (completedTasks + completedReminders)
                .sortedByDescending { it.timestamp },
            carryForwardSuggestions = (overdueTasks + overdueReminders)
                .sortedBy { it.item.timestamp }
                .take(5),
            openLoops = openLoops,
            staleLoops = analyzer.findStaleLoops(
                tasks = data.tasks,
                captures = data.captures,
                staleLoopDays = data.settings.staleLoopDays,
                now = now,
            ),
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
        supportingText = when (status) {
            TaskStatus.WaitingFor -> "Waiting for"
            TaskStatus.Someday -> "Someday"
            else -> "Task"
        },
    )

    private fun CaptureEntity.asReviewItem() = ReviewItem(
        id = id,
        type = ReviewItemType.Capture,
        title = rawText,
        timestamp = updatedAt,
        supportingText = "Unfinalized inbox capture",
        reviewReason = reviewReason(),
    )

    private fun ReminderEntity.asReviewItem(timestamp: Long = dueAt) = ReviewItem(
        id = id,
        type = ReviewItemType.Reminder,
        title = title,
        timestamp = timestamp,
        supportingText = "Reminder",
    )

    private fun ReviewItem.asReviewLoop(): ReviewLoop = ReviewLoop(
        id = id,
        type = if (type == ReviewItemType.Capture) ReviewLoopType.Capture else ReviewLoopType.Task,
        title = title,
        updatedAt = timestamp,
    )

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReviewViewModel::class.java))
            return ReviewViewModel(container) as T
        }
    }
}

private fun ReviewData.asCorpus(): SearchCorpus =
    SearchCorpus(
        captures = captures,
        notes = notes,
        tasks = tasks,
        reminders = reminders,
        spaces = spaces,
    )

private fun AiRouteSource.tinyActionLabel(): String = when (this) {
    AiRouteSource.Gemini -> "Suggested by Gemini"
    AiRouteSource.Local -> "Local suggestion"
    AiRouteSource.GeminiFailedLocalUsed -> "Local fallback"
}

internal fun isUnresolvedReviewCapture(capture: CaptureEntity): Boolean =
    capture.status == CaptureStatus.Inbox

internal fun CaptureEntity.reviewReason(): String =
    "Needs review because it has not been finalized or dismissed."

internal class ReviewActions(
    private val captureRepository: CaptureRepository,
    private val taskRepository: TaskRepository,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val now: () -> Long = System::currentTimeMillis,
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
                captureRepository.update(
                    it.copy(status = CaptureStatus.Archived, updatedAt = now()),
                )
            }
        }
    }

    suspend fun completeTask(loop: ReviewLoop) {
        require(loop.type == ReviewLoopType.Task)
        taskRepository.getById(loop.id)?.let {
            val completedAt = now()
            taskRepository.update(
                it.copy(status = TaskStatus.Done, updatedAt = completedAt, completedAt = completedAt),
            )
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
        confirmCaptureAction.markCaptureReviewed(loop.id)
    }
}
