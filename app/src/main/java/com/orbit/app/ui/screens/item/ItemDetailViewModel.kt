package com.orbit.app.ui.screens.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.ai.AiRouteSource
import com.orbit.app.domain.analyzer.TinyActionSuggestion
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val isLoading: Boolean = true,
    val type: ItemDetailType,
    val itemId: Long,
    val title: String = "",
    val body: String = "",
    val rawText: String = "",
    val spaceId: Long? = null,
    val spaces: List<SpaceEntity> = emptyList(),
    val statusLabel: String = "",
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val dueAt: Long? = null,
    val scheduledDateEpochDay: Long? = null,
    val scheduledAt: Long? = null,
    val canEditTitle: Boolean = true,
    val canEditBody: Boolean = true,
    val canComplete: Boolean = false,
    val canArchive: Boolean = false,
    val isComplete: Boolean = false,
    val isArchived: Boolean = false,
    val taskStatus: TaskStatus? = null,
    val tinyActionSuggestion: TinyActionSuggestion? = null,
    val isCreatingTinyTask: Boolean = false,
    val isMissing: Boolean = false,
    val closeAfterDelete: Boolean = false,
    val archiveUndoOperationId: Long? = null,
    val scheduleUndoOperationId: Long? = null,
    val message: String? = null,
)

class ItemDetailViewModel(
    private val type: ItemDetailType,
    private val itemId: Long,
    private val container: OrbitContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ItemDetailUiState(type = type, itemId = itemId))
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val archiveUndo = ItemArchiveUndo(
        noteRepository = container.noteRepository,
        taskRepository = container.taskRepository,
        captureRepository = container.captureRepository,
    )
    private val scheduleActions = ItemScheduleActions(
        noteRepository = container.noteRepository,
        taskRepository = container.taskRepository,
    )

    init {
        load()
    }

    fun save(title: String, body: String, spaceId: Long?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (type) {
                    ItemDetailType.Note -> container.noteRepository.getById(itemId)?.let {
                        container.noteRepository.update(
                            it.copy(
                                title = title.trim().ifBlank { "Untitled note" },
                                body = body,
                                spaceId = spaceId,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Task -> container.taskRepository.getById(itemId)?.let {
                        container.taskRepository.update(
                            it.copy(
                                title = title.trim().ifBlank { "Untitled task" },
                                notes = body,
                                spaceId = spaceId,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Reminder -> container.reminderRepository.getById(itemId)?.let {
                        container.reminderRepository.update(
                            it.copy(
                                title = title.trim().ifBlank { "Untitled reminder" },
                                notes = body,
                                spaceId = spaceId,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Capture -> container.captureRepository.getById(itemId)?.let {
                        container.captureRepository.update(
                            it.copy(suggestedSpaceId = spaceId, updatedAt = now),
                        )
                    }
                }
            }.onSuccess {
                load(message = "Saved.")
            }.onFailure {
                _uiState.update { state -> state.copy(message = "Could not save this item.") }
            }
        }
    }

    fun updateSchedule(schedule: ItemSchedule) {
        viewModelScope.launch {
            runCatching { scheduleActions.apply(type, itemId, schedule) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is ScheduleOutcome.Applied -> load(
                            message = "Schedule updated.",
                            scheduleUndoOperationId = outcome.operationId,
                        )
                        ScheduleOutcome.Ignored -> load(message = "Schedule unchanged.")
                        ScheduleOutcome.Missing -> load(message = "This item is no longer available.")
                        ScheduleOutcome.Unsupported -> Unit
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(message = "Could not update the schedule.") }
                }
        }
    }

    fun undoSchedule(operationId: Long) {
        viewModelScope.launch {
            runCatching { scheduleActions.undo(operationId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        ScheduleUndoOutcome.Restored -> load(message = "Schedule restored.")
                        ScheduleUndoOutcome.Missing -> load(message = "This item is no longer available.")
                        ScheduleUndoOutcome.Stale -> messageShown(scheduleUndoOperationId = operationId)
                    }
                }
                .onFailure { load(message = "Could not restore the schedule.") }
        }
    }

    fun toggleComplete() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (type) {
                    ItemDetailType.Task -> container.taskRepository.getById(itemId)?.let {
                        val reopening = it.status == TaskStatus.Done
                        container.taskRepository.update(
                            it.copy(
                                status = if (reopening) TaskStatus.Open else TaskStatus.Done,
                                completedAt = if (reopening) null else now,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Reminder -> container.reminderRepository.getById(itemId)?.let {
                        container.reminderRepository.update(
                            it.copy(
                                completedAt = if (it.completedAt == null) now else null,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Capture -> container.captureRepository.getById(itemId)?.let {
                        container.captureRepository.update(
                            it.copy(
                                status = if (it.status == CaptureStatus.Processed) {
                                    CaptureStatus.Inbox
                                } else {
                                    CaptureStatus.Processed
                                },
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Note -> Unit
                }
            }.onSuccess {
                load(message = "Updated.")
            }.onFailure {
                _uiState.update { state -> state.copy(message = "Could not update this item.") }
            }
        }
    }

    fun setTaskStatus(status: TaskStatus) {
        if (type != ItemDetailType.Task || status == TaskStatus.Archived) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                container.taskRepository.getById(itemId)?.let {
                    container.taskRepository.update(
                        it.copy(
                            status = status,
                            completedAt = if (status == TaskStatus.Done) now else null,
                            updatedAt = now,
                        ),
                    )
                }
            }.onSuccess {
                load(message = status.statusMessage())
            }.onFailure {
                _uiState.update { state -> state.copy(message = "Could not update this item.") }
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            runCatching {
                archiveUndo.archive(type, itemId)
            }.onSuccess { outcome ->
                when (outcome) {
                    is ArchiveOutcome.Archived -> load(
                        message = "Archived.",
                        archiveUndoOperationId = outcome.operationId,
                    )
                    ArchiveOutcome.Missing -> load(message = "This item is no longer available.")
                    ArchiveOutcome.Ignored,
                    ArchiveOutcome.Unsupported,
                    -> Unit
                }
            }.onFailure {
                _uiState.update { state -> state.copy(message = "Could not archive this item.") }
            }
        }
    }

    fun undoArchive(operationId: Long) {
        viewModelScope.launch {
            runCatching {
                archiveUndo.undo(operationId)
            }.onSuccess { outcome ->
                when (outcome) {
                    UndoOutcome.Restored -> load(message = "Restored.")
                    UndoOutcome.Ignored -> Unit
                    UndoOutcome.Stale -> messageShown(operationId)
                }
            }.onFailure {
                load(message = "Could not restore this item.")
            }
        }
    }

    fun deleteProtected() {
        viewModelScope.launch {
            runCatching {
                when (type) {
                    ItemDetailType.Note -> container.noteRepository.deleteById(itemId)
                    ItemDetailType.Task -> container.taskRepository.deleteById(itemId)
                    ItemDetailType.Reminder -> container.reminderRepository.deleteById(itemId)
                    ItemDetailType.Capture -> container.captureRepository.deleteById(itemId)
                }
            }.onSuccess {
                _uiState.update { it.copy(closeAfterDelete = true) }
            }.onFailure {
                _uiState.update { state -> state.copy(message = "Could not delete this item.") }
            }
        }
    }

    fun makeSmaller() {
        val state = _uiState.value
        if (state.isLoading || state.isMissing) return
        val sourceText = state.title
            .ifBlank { state.body }
            .ifBlank { state.rawText }
            .ifBlank { "this item" }
        viewModelScope.launch {
            val settings = container.appSettingsRepository.settings.first()
            val routedAction = container.aiRouter.makeSmaller(sourceText, settings)
            _uiState.update {
                it.copy(
                    tinyActionSuggestion = TinyActionSuggestion(
                        sourceKey = "${state.type.name}_${state.itemId}",
                        sourceTitle = sourceText,
                        action = routedAction.action,
                        sourceLabel = routedAction.metadata.source.tinyActionLabel(),
                    ),
                    message = if (routedAction.metadata.source == com.orbit.app.domain.ai.AiRouteSource.GeminiFailedLocalUsed) {
                        routedAction.metadata.error?.userMessage
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun dismissTinyAction() {
        _uiState.update { it.copy(tinyActionSuggestion = null) }
    }

    fun createTinyTask() {
        val state = _uiState.value
        val suggestion = state.tinyActionSuggestion ?: return
        if (state.isCreatingTinyTask) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingTinyTask = true, message = null) }
            runCatching {
                container.taskRepository.insert(
                    TaskEntity(
                        title = suggestion.action.removeSuffix("."),
                        notes = "Made smaller from: ${suggestion.sourceTitle}",
                        spaceId = state.spaceId,
                    ),
                )
            }.onSuccess {
                load(message = "Tiny task created.")
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isCreatingTinyTask = false,
                        message = "Could not create the tiny task.",
                    )
                }
            }
        }
    }

    fun messageShown(
        archiveUndoOperationId: Long? = null,
        scheduleUndoOperationId: Long? = null,
    ) {
        archiveUndoOperationId?.let(archiveUndo::expire)
        scheduleUndoOperationId?.let(scheduleActions::expire)
        _uiState.update { state ->
            if (
                (archiveUndoOperationId == null || state.archiveUndoOperationId == archiveUndoOperationId) &&
                (scheduleUndoOperationId == null || state.scheduleUndoOperationId == scheduleUndoOperationId)
            ) {
                state.copy(
                    message = null,
                    archiveUndoOperationId = null,
                    scheduleUndoOperationId = null,
                )
            } else {
                state
            }
        }
    }

    private fun load(
        message: String? = null,
        archiveUndoOperationId: Long? = null,
        scheduleUndoOperationId: Long? = null,
    ) {
        viewModelScope.launch {
            val spaces = container.spaceRepository.observeAll().replaySafeFirst()
                .filterNot { it.hidden || it.archived }
            when (type) {
                ItemDetailType.Note -> {
                    val note = container.noteRepository.getById(itemId)
                    _uiState.value = note?.asState(
                        spaces,
                        message,
                        archiveUndoOperationId,
                        scheduleUndoOperationId,
                    )
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Task -> {
                    val task = container.taskRepository.getById(itemId)
                    _uiState.value = task?.asState(
                        spaces,
                        message,
                        archiveUndoOperationId,
                        scheduleUndoOperationId,
                    )
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Reminder -> {
                    val reminder = container.reminderRepository.getById(itemId)
                    _uiState.value = reminder?.asState(spaces, message)
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Capture -> {
                    val capture = container.captureRepository.getById(itemId)
                    _uiState.value = capture?.asState(spaces, message, archiveUndoOperationId)
                        ?: missingState(spaces, message)
                }
            }
        }
    }

    private fun missingState(spaces: List<SpaceEntity>, message: String?) = ItemDetailUiState(
        isLoading = false,
        type = type,
        itemId = itemId,
        spaces = spaces,
        isMissing = true,
        message = message,
    )

    private suspend fun kotlinx.coroutines.flow.Flow<List<SpaceEntity>>.replaySafeFirst(): List<SpaceEntity> =
        first()

    private fun NoteEntity.asState(
        spaces: List<SpaceEntity>,
        message: String?,
        archiveUndoOperationId: Long?,
        scheduleUndoOperationId: Long?,
    ) = ItemDetailUiState(
        isLoading = false,
        type = ItemDetailType.Note,
        itemId = id,
        title = title,
        body = body,
        spaceId = spaceId,
        spaces = spaces,
        statusLabel = if (archived) "Archived note" else "Note",
        createdAt = createdAt,
        updatedAt = updatedAt,
        scheduledDateEpochDay = scheduledDateEpochDay,
        scheduledAt = scheduledAt,
        canComplete = false,
        canArchive = !archived,
        isArchived = archived,
        archiveUndoOperationId = archiveUndoOperationId,
        scheduleUndoOperationId = scheduleUndoOperationId,
        message = message,
    )

    private fun TaskEntity.asState(
        spaces: List<SpaceEntity>,
        message: String?,
        archiveUndoOperationId: Long?,
        scheduleUndoOperationId: Long?,
    ) = ItemDetailUiState(
        isLoading = false,
        type = ItemDetailType.Task,
        itemId = id,
        title = title,
        body = notes,
        spaceId = spaceId,
        spaces = spaces,
        statusLabel = status.label(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        dueAt = dueAt,
        scheduledDateEpochDay = scheduledDateEpochDay,
        scheduledAt = dueAt,
        canComplete = status != TaskStatus.Archived,
        canArchive = status != TaskStatus.Archived,
        isComplete = status == TaskStatus.Done,
        isArchived = status == TaskStatus.Archived,
        taskStatus = status,
        archiveUndoOperationId = archiveUndoOperationId,
        scheduleUndoOperationId = scheduleUndoOperationId,
        message = message,
    )

    private fun ReminderEntity.asState(
        spaces: List<SpaceEntity>,
        message: String?,
    ) = ItemDetailUiState(
        isLoading = false,
        type = ItemDetailType.Reminder,
        itemId = id,
        title = title,
        body = notes,
        spaceId = spaceId,
        spaces = spaces,
        statusLabel = if (completedAt == null) "Reminder" else "Completed reminder",
        createdAt = createdAt,
        updatedAt = updatedAt,
        dueAt = dueAt,
        canComplete = true,
        canArchive = false,
        isComplete = completedAt != null,
        message = message,
    )

    private fun CaptureEntity.asState(
        spaces: List<SpaceEntity>,
        message: String?,
        archiveUndoOperationId: Long?,
    ) = ItemDetailUiState(
        isLoading = false,
        type = ItemDetailType.Capture,
        itemId = id,
        title = "Capture",
        rawText = rawText,
        spaceId = suggestedSpaceId,
        spaces = spaces,
        statusLabel = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        canEditTitle = false,
        canEditBody = false,
        canComplete = status != CaptureStatus.Archived,
        canArchive = status != CaptureStatus.Archived,
        isComplete = status == CaptureStatus.Processed,
        isArchived = status == CaptureStatus.Archived,
        archiveUndoOperationId = archiveUndoOperationId,
        message = message,
    )

    class Factory(
        private val type: ItemDetailType,
        private val itemId: Long,
        private val container: OrbitContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ItemDetailViewModel::class.java))
            return ItemDetailViewModel(type, itemId, container) as T
        }
    }
}

private fun AiRouteSource.tinyActionLabel(): String = when (this) {
    AiRouteSource.Gemini -> "Suggested by Gemini"
    AiRouteSource.Local -> "Local suggestion"
    AiRouteSource.GeminiFailedLocalUsed -> "Local fallback"
}

private fun TaskStatus.label(): String = when (this) {
    TaskStatus.Open -> "Task"
    TaskStatus.Done -> "Done"
    TaskStatus.Archived -> "Archived"
    TaskStatus.WaitingFor -> "Waiting for"
    TaskStatus.Someday -> "Someday"
}

private fun TaskStatus.statusMessage(): String = when (this) {
    TaskStatus.Open -> "Moved to active tasks."
    TaskStatus.Done -> "Completed."
    TaskStatus.WaitingFor -> "Marked as waiting for."
    TaskStatus.Someday -> "Moved to Someday."
    TaskStatus.Archived -> "Archived."
}
