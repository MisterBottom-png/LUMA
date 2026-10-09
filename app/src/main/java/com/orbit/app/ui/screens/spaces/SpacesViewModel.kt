package com.orbit.app.ui.screens.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.DuplicateSpaceNameException
import com.orbit.app.data.local.SpaceNames
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class SpaceContents(
    val notes: List<NoteEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
) {
    val size: Int get() = notes.size + tasks.size + reminders.size
}

data class SpaceContentSections(
    val needsAttention: SpaceContents,
    val upcoming: SpaceContents,
    val recentAndReference: SpaceContents,
)

internal fun SpaceContents.sectioned(now: Long): SpaceContentSections {
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .toEpochDay()
    val attentionTasks = tasks.filter {
        it.status == TaskStatus.Open &&
            ((it.dueAt != null && it.dueAt < now) ||
                (it.scheduledDateEpochDay != null && it.scheduledDateEpochDay <= today))
    }
    val attentionReminders = reminders.filter { it.completedAt == null && it.dueAt < now }
    val upcomingTasks = tasks.filter {
        it.status == TaskStatus.Open &&
            ((it.dueAt != null && it.dueAt >= now) ||
                (it.scheduledDateEpochDay != null && it.scheduledDateEpochDay > today))
    }
    val upcomingReminders = reminders.filter { it.completedAt == null && it.dueAt >= now }
    return SpaceContentSections(
        needsAttention = SpaceContents(tasks = attentionTasks, reminders = attentionReminders),
        upcoming = SpaceContents(tasks = upcomingTasks, reminders = upcomingReminders),
        recentAndReference = SpaceContents(
            notes = notes,
            tasks = tasks.filterNot { it in attentionTasks || it in upcomingTasks },
            reminders = reminders.filterNot { it in attentionReminders || it in upcomingReminders },
        ),
    )
}

data class SpacesUiState(
    val spaces: List<SpaceEntity> = emptyList(),
    val visibleSpaces: List<SpaceEntity> = emptyList(),
    val archivedSpaces: List<SpaceEntity> = emptyList(),
    val hiddenSpaces: List<SpaceEntity> = emptyList(),
    val selectedSpace: SpaceEntity? = null,
    val isUnfiledSelected: Boolean = false,
    val hasUnfiledItems: Boolean = false,
    val selectedContents: SpaceContents = SpaceContents(),
    val itemCounts: Map<Long, Int> = emptyMap(),
    /** The earliest upcoming open task or reminder per Space, for the overview card. */
    val nextItems: Map<Long, SpaceNextItem> = emptyMap(),
    /** Saved thoughts not yet sorted; they live in Review > To sort. */
    val toSortCount: Int = 0,
    val moveUndo: SpaceMoveUndo? = null,
    val moveFailure: SpaceMoveFailure? = null,
)

/** [hasTime] is false for a task planned for a day, which must not read as 00:00. */
data class SpaceNextItem(val title: String, val at: Long, val hasTime: Boolean = true)

data class SpaceMoveUndo(val item: SpaceItemReference, val previousSpaceId: Long?)

data class SpaceMoveFailure(val item: SpaceItemReference, val targetSpaceId: Long?)

enum class SpaceItemType { Note, Task, Reminder }

data class SpaceItemReference(
    val type: SpaceItemType,
    val id: Long,
)

private data class AllSpaceContents(
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val toSortCount: Int,
)

private data class SpaceMoveFeedback(
    val undo: SpaceMoveUndo?,
    val failure: SpaceMoveFailure?,
)

class SpacesViewModel(private val container: OrbitContainer) : ViewModel() {
    private val selectedSpaceId = MutableStateFlow<Long?>(null)
    private val isUnfiledSelected = MutableStateFlow(false)
    private val moveUndo = MutableStateFlow<SpaceMoveUndo?>(null)
    private val moveFailure = MutableStateFlow<SpaceMoveFailure?>(null)
    private val itemMoveActions = SpaceItemMoveActions(
        noteRepository = container.noteRepository,
        taskRepository = container.taskRepository,
        reminderRepository = container.reminderRepository,
    )

    private val allContents = combine(
        container.noteRepository.observeAll(),
        container.taskRepository.observeAll(),
        container.reminderRepository.observeAll(),
        container.captureRepository.observeAll(),
    ) { notes, tasks, reminders, captures ->
        AllSpaceContents(notes, tasks, reminders, captures.count { it.status == CaptureStatus.Inbox })
    }
    private val moveFeedback = combine(moveUndo, moveFailure) { undo, failure ->
        SpaceMoveFeedback(undo, failure)
    }

    val uiState = combine(
        container.spaceRepository.observeAll(),
        allContents,
        selectedSpaceId,
        isUnfiledSelected,
        moveFeedback,
    ) { spaces, contents, selectedId, unfiledSelected, feedback ->
        val (visibleSpaces, archivedSpaces, hiddenSpaces) = partitionSpaces(spaces)
        val selectedContents = if (selectedId != null || unfiledSelected) {
            SpaceContents(
                notes = contents.notes.filter { it.spaceId == selectedId && !it.archived },
                tasks = contents.tasks.filter {
                    it.spaceId == selectedId && it.status != TaskStatus.Archived
                },
                reminders = contents.reminders.filter { it.spaceId == selectedId },
            )
        } else {
            SpaceContents()
        }
        SpacesUiState(
            spaces = spaces,
            visibleSpaces = visibleSpaces,
            archivedSpaces = archivedSpaces,
            hiddenSpaces = hiddenSpaces,
            selectedSpace = spaces.firstOrNull { it.id == selectedId },
            isUnfiledSelected = unfiledSelected,
            hasUnfiledItems = hasUnfiledFinalizedItems(contents.notes, contents.tasks, contents.reminders),
            selectedContents = selectedContents,
            toSortCount = contents.toSortCount,
            nextItems = calculateSpaceNextItems(
                tasks = contents.tasks,
                reminders = contents.reminders,
                now = System.currentTimeMillis(),
            ),
            itemCounts = calculateSpaceItemCounts(
                spaces = spaces,
                notes = contents.notes,
                tasks = contents.tasks,
                reminders = contents.reminders,
            ),
            moveUndo = feedback.undo,
            moveFailure = feedback.failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SpacesUiState(),
    )

    fun selectSpace(spaceId: Long?) {
        selectedSpaceId.value = spaceId
        isUnfiledSelected.value = false
    }

    fun selectUnfiled() {
        selectedSpaceId.value = null
        isUnfiledSelected.value = true
    }

    fun createSpace(name: String, icon: String, colorAccent: String) {
        val cleanName = cleanSpaceName(name)
        if (cleanName.isEmpty() || !canUseSpaceName(cleanName, uiState.value.spaces)) return
        viewModelScope.launch {
            val nextOrder = (uiState.value.spaces.maxOfOrNull { it.sortOrder } ?: -1) + 1
            try {
                container.spaceRepository.insert(
                    SpaceEntity(
                        name = cleanName,
                        icon = icon,
                        colorAccent = colorAccent,
                        sortOrder = nextOrder,
                    ),
                )
            } catch (_: DuplicateSpaceNameException) {
                // Another tap created the same Space first; the existing one stays.
            }
        }
    }

    fun updateSpace(spaceId: Long, name: String, icon: String, colorAccent: String) {
        val cleanName = cleanSpaceName(name)
        if (cleanName.isEmpty() || !canUseSpaceName(cleanName, uiState.value.spaces, spaceId)) return
        updateStoredSpace(spaceId) {
            it.copy(
                name = cleanName,
                icon = icon,
                colorAccent = colorAccent,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun hideSpace(spaceId: Long) {
        updateStoredSpace(spaceId) {
            it.copy(hidden = true, updatedAt = System.currentTimeMillis())
        }
        if (selectedSpaceId.value == spaceId) selectSpace(null)
    }

    fun archiveSpace(spaceId: Long) {
        updateStoredSpace(spaceId) {
            it.copy(archived = true, hidden = false, updatedAt = System.currentTimeMillis())
        }
        if (selectedSpaceId.value == spaceId) selectSpace(null)
    }

    fun restoreSpace(spaceId: Long) {
        updateStoredSpace(spaceId) {
            it.copy(hidden = false, archived = false, updatedAt = System.currentTimeMillis())
        }
    }

    fun moveSpace(spaceId: Long, direction: Int) {
        val ordered = uiState.value.visibleSpaces
        val currentIndex = ordered.indexOfFirst { it.id == spaceId }
        val targetIndex = currentIndex + direction
        if (currentIndex == -1 || targetIndex !in ordered.indices) return

        val current = ordered[currentIndex]
        val target = ordered[targetIndex]
        viewModelScope.launch {
            container.spaceRepository.swapSortOrder(
                firstId = current.id,
                secondId = target.id,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun moveItem(item: SpaceItemReference, targetSpaceId: Long?) {
        viewModelScope.launch {
            when (val outcome = itemMoveActions.move(item, targetSpaceId)) {
                is SpaceItemMoveOutcome.Moved -> {
                    moveUndo.value = outcome.undo
                    moveFailure.value = null
                }

                SpaceItemMoveOutcome.Failed -> moveFailure.value = SpaceMoveFailure(item, targetSpaceId)
                SpaceItemMoveOutcome.Missing,
                SpaceItemMoveOutcome.Unchanged -> Unit
            }
        }
    }

    fun retryFailedMove() {
        moveFailure.value?.let { moveItem(it.item, it.targetSpaceId) }
    }

    fun dismissMoveFailure() {
        moveFailure.value = null
    }

    fun undoLastMove() {
        val undo = moveUndo.value ?: return
        viewModelScope.launch {
            if (itemMoveActions.restore(undo)) {
                moveUndo.value = null
            }
        }
    }

    private fun updateStoredSpace(
        spaceId: Long,
        transform: (SpaceEntity) -> SpaceEntity,
    ) {
        viewModelScope.launch {
            container.spaceRepository.getById(spaceId)?.let { stored ->
                try {
                    container.spaceRepository.update(transform(stored))
                } catch (_: DuplicateSpaceNameException) {
                    // The editor already blocks taken names; a race keeps the stored name.
                }
            }
        }
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SpacesViewModel::class.java))
            return SpacesViewModel(container) as T
        }
    }
}

internal data class SpacePartition(
    val visible: List<SpaceEntity>,
    val archived: List<SpaceEntity>,
    val hidden: List<SpaceEntity>,
)

internal fun partitionSpaces(spaces: List<SpaceEntity>): SpacePartition {
    val ordered = spaces.sortedBy { it.sortOrder }
    return SpacePartition(
        visible = ordered.filter { !it.hidden && !it.archived },
        archived = ordered.filter { it.archived },
        hidden = ordered.filter { it.hidden && !it.archived },
    )
}

internal fun calculateSpaceItemCounts(
    spaces: List<SpaceEntity>,
    notes: List<NoteEntity>,
    tasks: List<TaskEntity>,
    reminders: List<ReminderEntity>,
): Map<Long, Int> {
    val counts = LinkedHashMap<Long, Int>(spaces.size)
    spaces.forEach { counts[it.id] = 0 }

    fun increment(spaceId: Long?) {
        if (spaceId != null && counts.containsKey(spaceId)) {
            counts[spaceId] = counts.getValue(spaceId) + 1
        }
    }

    notes.forEach { if (!it.archived) increment(it.spaceId) }
    tasks.forEach { if (it.status != TaskStatus.Archived) increment(it.spaceId) }
    reminders.forEach { if (it.completedAt == null) increment(it.spaceId) }
    return counts
}

internal fun calculateSpaceNextItems(
    tasks: List<TaskEntity>,
    reminders: List<ReminderEntity>,
    now: Long,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): Map<Long, SpaceNextItem> {
    val candidates = buildList {
        tasks.filter { it.status == TaskStatus.Open && it.spaceId != null }.forEach { task ->
            when {
                task.dueAt != null -> if (task.dueAt >= now) add(task.spaceId!! to SpaceNextItem(task.title, task.dueAt))
                task.scheduledDateEpochDay != null -> {
                    val dayStart = java.time.LocalDate.ofEpochDay(task.scheduledDateEpochDay)
                        .atStartOfDay(zoneId).toInstant().toEpochMilli()
                    if (dayStart >= startOfDay(now, zoneId)) {
                        add(task.spaceId!! to SpaceNextItem(task.title, dayStart, hasTime = false))
                    }
                }
            }
        }
        reminders.filter { it.completedAt == null && it.spaceId != null && it.dueAt >= now }.forEach {
            add(it.spaceId!! to SpaceNextItem(it.title, it.dueAt))
        }
    }
    return candidates
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, items) -> items.minBy { it.at } }
}

private fun startOfDay(now: Long, zoneId: java.time.ZoneId): Long =
    java.time.Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

internal fun hasUnfiledFinalizedItems(
    notes: List<NoteEntity>,
    tasks: List<TaskEntity>,
    reminders: List<ReminderEntity>,
): Boolean = notes.any { it.spaceId == null && !it.archived } ||
    tasks.any { it.spaceId == null && it.status != TaskStatus.Archived } ||
    reminders.any { it.spaceId == null }

internal fun cleanSpaceName(value: String): String = SpaceNames.clean(value)

internal fun normalizeSpaceName(value: String): String = SpaceNames.normalize(value)

internal fun canUseSpaceName(
    candidate: String,
    spaces: List<SpaceEntity>,
    excludingSpaceId: Long? = null,
): Boolean {
    val normalizedCandidate = normalizeSpaceName(candidate)
    return normalizedCandidate.isNotEmpty() && spaces.none {
        it.id != excludingSpaceId && normalizeSpaceName(it.name) == normalizedCandidate
    }
}
