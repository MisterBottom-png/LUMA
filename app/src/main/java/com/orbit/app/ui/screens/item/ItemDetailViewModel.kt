package com.orbit.app.ui.screens.item

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.R
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
import com.orbit.app.ui.localization.effectiveAppLocale
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
    val notificationOffsetMinutes: Long? = null,
    val notificationEnabled: Boolean? = null,
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
    val convertedToType: ItemDetailType? = null,
    val saveCompletedAt: Long? = null,
    val hasPendingBrainDump: Boolean = false,
)

class ItemDetailViewModel(
    private val type: ItemDetailType,
    private val itemId: Long,
    private val container: OrbitContainer,
) : ViewModel() {
    private var currentType = type
    private val localizedContext by lazy {
        val base = container.applicationContext
        base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocale(effectiveAppLocale(base))
            },
        )
    }
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
    private val typeConversion = ItemTypeConversion(container.database, container.reminderScheduler)

    init {
        load()
    }

    fun save(title: String, body: String, spaceId: Long?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (currentType) {
                    ItemDetailType.Note -> container.noteRepository.getById(itemId)?.let {
                        container.noteRepository.update(
                            it.copy(
                                title = title.trim(),
                                body = body,
                                spaceId = spaceId,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Task -> container.taskRepository.getById(itemId)?.let {
                        container.taskRepository.update(
                            it.copy(
                                title = title.trim(),
                                notes = body,
                                spaceId = spaceId,
                                updatedAt = now,
                            ),
                        )
                    }

                    ItemDetailType.Reminder -> container.reminderRepository.getById(itemId)?.let {
                        container.reminderRepository.update(
                            it.copy(
                                title = title.trim(),
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
                load(message = localized(R.string.core_item_detail_saved), saveCompletedAt = now)
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_save_failed)) }
            }
        }
    }

    fun updateSchedule(schedule: ItemSchedule) {
        viewModelScope.launch {
            if (currentType == ItemDetailType.Reminder) {
                val timed = schedule as? ItemSchedule.Timed ?: return@launch
                runCatching {
                    container.reminderRepository.getById(itemId)?.let {
                        container.reminderRepository.update(
                            it.copy(dueAt = timed.epochMillis, updatedAt = System.currentTimeMillis()),
                        )
                    }
                }.onSuccess { load(message = localized(R.string.core_item_detail_schedule_updated)) }
                    .onFailure { load(message = localized(R.string.core_item_detail_schedule_update_failed)) }
                return@launch
            }
            runCatching { scheduleActions.apply(currentType, itemId, schedule) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is ScheduleOutcome.Applied -> load(
                            message = localized(R.string.core_item_detail_schedule_updated),
                            scheduleUndoOperationId = outcome.operationId,
                        )
                        ScheduleOutcome.Ignored -> load(message = localized(R.string.core_item_detail_schedule_unchanged))
                        ScheduleOutcome.Missing -> load(message = localized(R.string.core_item_detail_unavailable_message))
                        ScheduleOutcome.Unsupported -> Unit
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_schedule_update_failed)) }
                }
        }
    }

    fun updateNotificationOffset(offsetMinutes: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                container.reminderRepository.getById(itemId)?.let {
                    container.reminderRepository.update(
                        it.copy(notificationOffsetMinutes = offsetMinutes, updatedAt = now),
                    )
                }
            }.onSuccess {
                load(message = localized(R.string.core_item_detail_notification_updated))
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_notification_update_failed)) }
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                container.reminderRepository.getById(itemId)?.let {
                    container.reminderRepository.update(
                        it.copy(notificationEnabled = enabled, updatedAt = now),
                    )
                }
            }.onSuccess {
                load(message = localized(R.string.core_item_detail_notification_updated))
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_notification_update_failed)) }
            }
        }
    }

    fun updateSpace(spaceId: Long?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (currentType) {
                    ItemDetailType.Note -> container.noteRepository.getById(itemId)?.let {
                        container.noteRepository.update(it.copy(spaceId = spaceId, updatedAt = now))
                    }
                    ItemDetailType.Task -> container.taskRepository.getById(itemId)?.let {
                        container.taskRepository.update(it.copy(spaceId = spaceId, updatedAt = now))
                    }
                    ItemDetailType.Reminder -> container.reminderRepository.getById(itemId)?.let {
                        container.reminderRepository.update(it.copy(spaceId = spaceId, updatedAt = now))
                    }
                    ItemDetailType.Capture -> container.captureRepository.getById(itemId)?.let {
                        container.captureRepository.update(it.copy(suggestedSpaceId = spaceId, updatedAt = now))
                    }
                }
            }.onSuccess { load(message = localized(R.string.core_item_detail_space_updated)) }
                .onFailure { load(message = localized(R.string.core_item_detail_space_update_failed)) }
        }
    }

    fun restore() {
        if (!_uiState.value.isArchived) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (currentType) {
                    ItemDetailType.Note -> container.noteRepository.getById(itemId)?.let {
                        container.noteRepository.update(it.copy(archived = false, updatedAt = now))
                    }
                    ItemDetailType.Task -> container.taskRepository.getById(itemId)?.let {
                        container.taskRepository.update(
                            it.copy(status = TaskStatus.Open, completedAt = null, updatedAt = now),
                        )
                    }
                    ItemDetailType.Capture -> container.captureRepository.getById(itemId)?.let {
                        container.captureRepository.update(
                            it.copy(status = CaptureStatus.Inbox, updatedAt = now),
                        )
                    }
                    ItemDetailType.Reminder -> Unit
                }
            }.onSuccess { load(message = localized(R.string.core_item_detail_restored)) }
                .onFailure { load(message = localized(R.string.core_item_detail_restore_failed)) }
        }
    }

    fun undoSchedule(operationId: Long) {
        viewModelScope.launch {
            runCatching { scheduleActions.undo(operationId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        ScheduleUndoOutcome.Restored -> load(message = localized(R.string.core_item_detail_schedule_restored))
                        ScheduleUndoOutcome.Missing -> load(message = localized(R.string.core_item_detail_unavailable_message))
                        ScheduleUndoOutcome.Stale -> messageShown(scheduleUndoOperationId = operationId)
                    }
                }
                .onFailure { load(message = localized(R.string.core_item_detail_schedule_restore_failed)) }
        }
    }

    fun toggleComplete() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (currentType) {
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
                load(message = localized(R.string.core_item_detail_updated_message))
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_update_failed)) }
            }
        }
    }

    fun setTaskStatus(status: TaskStatus) {
        if (currentType != ItemDetailType.Task || status == TaskStatus.Archived) return
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
                load(message = statusMessage(status))
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_update_failed)) }
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            runCatching {
                archiveUndo.archive(currentType, itemId)
            }.onSuccess { outcome ->
                when (outcome) {
                    is ArchiveOutcome.Archived -> load(
                        message = localized(R.string.core_item_detail_archived),
                        archiveUndoOperationId = outcome.operationId,
                    )
                    ArchiveOutcome.Missing -> load(message = localized(R.string.core_item_detail_unavailable_message))
                    ArchiveOutcome.Ignored,
                    ArchiveOutcome.Unsupported,
                    -> Unit
                }
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_archive_failed)) }
            }
        }
    }

    fun undoArchive(operationId: Long) {
        viewModelScope.launch {
            runCatching {
                archiveUndo.undo(operationId)
            }.onSuccess { outcome ->
                when (outcome) {
                    UndoOutcome.Restored -> load(message = localized(R.string.core_item_detail_restored))
                    UndoOutcome.Ignored -> Unit
                    UndoOutcome.Stale -> messageShown(operationId)
                }
            }.onFailure {
                load(message = localized(R.string.core_item_detail_restore_failed))
            }
        }
    }

    fun deleteProtected() {
        viewModelScope.launch {
            runCatching {
                when (currentType) {
                    ItemDetailType.Note -> container.noteRepository.deleteById(itemId)
                    ItemDetailType.Task -> container.taskRepository.deleteById(itemId)
                    ItemDetailType.Reminder -> container.reminderRepository.deleteById(itemId)
                    ItemDetailType.Capture -> container.captureRepository.deleteById(itemId)
                }
            }.onSuccess {
                _uiState.update { it.copy(closeAfterDelete = true) }
            }.onFailure {
                _uiState.update { state -> state.copy(message = localized(R.string.core_item_detail_delete_failed)) }
            }
        }
    }

    fun makeSmaller() {
        val state = _uiState.value
        if (state.isLoading || state.isMissing) return
        val sourceText = state.title
            .ifBlank { state.body }
            .ifBlank { state.rawText }
            .ifBlank { localized(R.string.core_item_detail_default_source) }
        viewModelScope.launch {
            val settings = container.appSettingsRepository.settings.first()
            val routedAction = container.aiRouter.makeSmaller(sourceText, settings)
            _uiState.update {
                it.copy(
                    tinyActionSuggestion = TinyActionSuggestion(
                        sourceKey = "${state.type.name}_${state.itemId}",
                        sourceTitle = sourceText,
                        action = routedAction.action,
                    sourceLabel = tinyActionLabel(routedAction.metadata.source),
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

    fun changeType(targetType: ItemDetailType, reminderDueAt: Long? = null) {
        if (targetType == currentType || targetType == ItemDetailType.Capture) return
        val dueAt = reminderDueAt ?: _uiState.value.scheduledAt
        viewModelScope.launch {
            runCatching { typeConversion.convert(currentType, itemId, targetType, dueAt) }
                .onSuccess { outcome ->
                    when (outcome) {
                        TypeConversionOutcome.Converted -> {
                            currentType = targetType
                            load(message = localized(R.string.core_item_detail_type_changed), convertedToType = targetType)
                        }
                        TypeConversionOutcome.Conflict -> load(
                            message = localized(R.string.core_item_detail_type_conflict),
                        )
                        TypeConversionOutcome.Missing -> load(message = localized(R.string.core_item_detail_unavailable_message))
                        TypeConversionOutcome.Unsupported -> load(message = localized(R.string.core_item_detail_reminder_time_required))
                    }
                }
                .onFailure { load(message = localized(R.string.core_item_detail_type_change_failed)) }
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
                        notes = localized(R.string.core_item_detail_tiny_task_note, suggestion.sourceTitle),
                        spaceId = state.spaceId,
                    ),
                )
            }.onSuccess {
                load(message = localized(R.string.core_item_detail_tiny_task_created))
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isCreatingTinyTask = false,
                        message = localized(R.string.core_item_detail_tiny_task_failed),
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
        convertedToType: ItemDetailType? = null,
        saveCompletedAt: Long? = null,
    ) {
        viewModelScope.launch {
            val spaces = container.spaceRepository.observeAll().replaySafeFirst()
                .filterNot { it.hidden || it.archived }
            val loaded = when (currentType) {
                ItemDetailType.Note -> {
                    val note = container.noteRepository.getById(itemId)
                    note?.asState(
                        spaces,
                        message,
                        archiveUndoOperationId,
                        scheduleUndoOperationId,
                    )
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Task -> {
                    val task = container.taskRepository.getById(itemId)
                    task?.asState(
                        spaces,
                        message,
                        archiveUndoOperationId,
                        scheduleUndoOperationId,
                    )
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Reminder -> {
                    val reminder = container.reminderRepository.getById(itemId)
                    reminder?.asState(spaces, message)
                        ?: missingState(spaces, message)
                }

                ItemDetailType.Capture -> {
                    val capture = container.captureRepository.getById(itemId)
                    val hasPendingBrainDump = container.brainDumpRepository.getSession(itemId) != null
                    capture?.asState(spaces, message, archiveUndoOperationId, hasPendingBrainDump)
                        ?: missingState(spaces, message)
                }
            }
            _uiState.value = loaded.copy(
                convertedToType = convertedToType,
                saveCompletedAt = saveCompletedAt,
            )
        }
    }

    private fun missingState(spaces: List<SpaceEntity>, message: String?) = ItemDetailUiState(
        isLoading = false,
        type = currentType,
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
        statusLabel = localized(
            if (archived) R.string.core_item_detail_archived_note else R.string.core_item_detail_type_note,
        ),
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
        statusLabel = statusLabel(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        dueAt = dueAt,
        scheduledAt = dueAt,
        scheduledDateEpochDay = scheduledDateEpochDay,
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
        statusLabel = localized(
            if (completedAt == null) {
                R.string.core_item_detail_type_reminder
            } else {
                R.string.core_item_detail_status_completed_reminder
            },
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
        dueAt = dueAt,
        scheduledAt = dueAt,
        notificationOffsetMinutes = notificationOffsetMinutes,
        notificationEnabled = notificationEnabled,
        canComplete = true,
        canArchive = false,
        isComplete = completedAt != null,
        message = message,
    )

    private fun CaptureEntity.asState(
        spaces: List<SpaceEntity>,
        message: String?,
        archiveUndoOperationId: Long?,
        hasPendingBrainDump: Boolean,
    ) = ItemDetailUiState(
        isLoading = false,
        type = ItemDetailType.Capture,
        itemId = id,
        title = localized(R.string.core_item_detail_type_capture),
        rawText = rawText,
        spaceId = suggestedSpaceId,
        spaces = spaces,
        statusLabel = captureStatusLabel(status),
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
        hasPendingBrainDump = hasPendingBrainDump,
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

    private fun localized(@StringRes resId: Int, vararg formatArgs: Any): String =
        localizedContext.getString(resId, *formatArgs)

    private fun tinyActionLabel(source: AiRouteSource): String = localized(
        when (source) {
            AiRouteSource.Gemini -> R.string.core_item_detail_ai_gemini
            AiRouteSource.Local -> R.string.core_item_detail_ai_local
            AiRouteSource.GeminiFailedLocalUsed -> R.string.core_item_detail_ai_local_fallback
        },
    )

    private fun statusLabel(status: TaskStatus): String = localized(
        when (status) {
            TaskStatus.Open -> R.string.core_item_detail_type_task
            TaskStatus.Done -> R.string.core_item_detail_status_done
            TaskStatus.Archived -> R.string.core_item_detail_status_archived
            TaskStatus.WaitingFor -> R.string.core_item_detail_status_waiting_for
            TaskStatus.Someday -> R.string.core_item_detail_status_someday
        },
    )

    private fun captureStatusLabel(status: CaptureStatus): String = localized(
        when (status) {
            CaptureStatus.Inbox -> R.string.core_item_detail_status_inbox
            CaptureStatus.Processed -> R.string.core_item_detail_status_processed
            CaptureStatus.Archived -> R.string.core_item_detail_status_archived
        },
    )

    private fun statusMessage(status: TaskStatus): String = localized(
        when (status) {
            TaskStatus.Open -> R.string.core_item_detail_active_tasks
            TaskStatus.Done -> R.string.core_item_detail_completed
            TaskStatus.WaitingFor -> R.string.core_item_detail_marked_waiting
            TaskStatus.Someday -> R.string.core_item_detail_moved_someday
            TaskStatus.Archived -> R.string.core_item_detail_archived
        },
    )
}
