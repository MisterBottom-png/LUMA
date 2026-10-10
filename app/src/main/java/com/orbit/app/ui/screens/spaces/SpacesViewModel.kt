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

/** One task or reminder in an opened Space. [at] is null for a task with no date. */
internal data class SpaceAgendaItem(
    val reference: SpaceItemReference,
    val title: String,
    val at: Long?,
    val hasTime: Boolean,
    val isReminder: Boolean,
    val isDone: Boolean,
)

internal data class SpaceAgenda(
    val today: List<SpaceAgendaItem>,
    val earlier: List<SpaceAgendaItem>,
    val upcoming: List<SpaceAgendaItem>,
    val noDate: List<SpaceAgendaItem>,
    val notes: List<NoteEntity>,
    val done: List<SpaceAgendaItem>,
)

/**
 * Splits an opened Space by day: today, from earlier, upcoming, no date, notes, and done.
 * Items in [keepInPlace] were ticked on this visit: they stay in their day section,
 * shown as done, instead of jumping to "done" under the finger.
 */
internal fun SpaceContents.agenda(
    now: Long,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    keepInPlace: Set<SpaceItemReference> = emptySet(),
): SpaceAgenda {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val todayRows = mutableListOf<SpaceAgendaItem>()
    val earlierRows = mutableListOf<SpaceAgendaItem>()
    val upcomingRows = mutableListOf<SpaceAgendaItem>()
    val noDateRows = mutableListOf<Pair<Long, SpaceAgendaItem>>()
    val doneRows = mutableListOf<Pair<Long, SpaceAgendaItem>>()

    fun place(item: SpaceAgendaItem, finishedAt: Long?, updatedAt: Long) {
        if (item.isDone && item.reference !in keepInPlace) {
            doneRows += (finishedAt ?: updatedAt) to item
            return
        }
        val at = item.at
        when {
            at == null -> noDateRows += updatedAt to item
            at < todayStart -> earlierRows += item
            at < tomorrowStart -> todayRows += item
            else -> upcomingRows += item
        }
    }

    tasks.filter { it.status != TaskStatus.Archived }.forEach { task ->
        val dayStart = task.scheduledDateEpochDay?.let {
            java.time.LocalDate.ofEpochDay(it).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        place(
            SpaceAgendaItem(
                reference = SpaceItemReference(SpaceItemType.Task, task.id),
                title = task.title,
                at = task.dueAt ?: dayStart,
                hasTime = task.dueAt != null,
                isReminder = false,
                isDone = task.status == TaskStatus.Done,
            ),
            finishedAt = task.completedAt,
            updatedAt = task.updatedAt,
        )
    }
    reminders.forEach { reminder ->
        place(
            SpaceAgendaItem(
                reference = SpaceItemReference(SpaceItemType.Reminder, reminder.id),
                title = reminder.title,
                at = reminder.dueAt,
                hasTime = true,
                isReminder = true,
                isDone = reminder.completedAt != null,
            ),
            finishedAt = reminder.completedAt,
            updatedAt = reminder.updatedAt,
        )
    }
    val byTime = compareBy<SpaceAgendaItem>({ it.at ?: Long.MAX_VALUE }, { !it.hasTime }, { it.title })
    return SpaceAgenda(
        today = todayRows.sortedWith(compareBy<SpaceAgendaItem>({ it.hasTime }, { it.at ?: 0L }, { it.title })),
        earlier = earlierRows.sortedWith(byTime),
        upcoming = upcomingRows.sortedWith(byTime),
        noDate = noDateRows.sortedByDescending { it.first }.map { it.second },
        notes = notes.filter { !it.archived }.sortedByDescending { it.updatedAt },
        done = doneRows.sortedByDescending { it.first }.map { it.second },
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
    /** Open tasks and reminders per Space: the number shown on the overview. */
    val openCounts: Map<Long, Int> = emptyMap(),
    /** Open tasks and reminders due today, across every Space and none. */
    val todayCount: Int = 0,
    /** The earliest upcoming open task or reminder per Space, for the overview card. */
    val nextItems: Map<Long, SpaceNextItem> = emptyMap(),
    /** Saved thoughts not yet sorted; they live in Review > To sort. */
    val toSortCount: Int = 0,
    val moveUndo: SpaceMoveUndo? = null,
    val moveFailure: SpaceMoveFailure? = null,
)

/**
 * [hasTime] is false for a task planned for a day, which must not read as 00:00.
 * [isEarlier] marks an item whose time has passed, shown only when nothing is ahead.
 */
data class SpaceNextItem(
    val title: String,
    val at: Long,
    val hasTime: Boolean = true,
    val isEarlier: Boolean = false,
)

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
            openCounts = calculateSpaceOpenCounts(spaces, contents.tasks, contents.reminders),
            todayCount = calculateTodayCount(contents.tasks, contents.reminders, System.currentTimeMillis()),
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

    /** Ticks a task off, or back on. A reminder is marked done the way its detail screen does it. */
    fun toggleDone(item: SpaceItemReference) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                when (item.type) {
                    SpaceItemType.Task -> container.taskRepository.getById(item.id)?.let {
                        val reopening = it.status == TaskStatus.Done
                        container.taskRepository.update(
                            it.copy(
                                status = if (reopening) TaskStatus.Open else TaskStatus.Done,
                                completedAt = if (reopening) null else now,
                                updatedAt = now,
                            ),
                        )
                    }
                    SpaceItemType.Reminder -> container.reminderRepository.getById(item.id)?.let {
                        container.reminderRepository.update(
                            if (it.completedAt == null) {
                                com.orbit.app.reminders.ReminderRepeats.markDone(it, now)
                            } else {
                                it.copy(completedAt = null, updatedAt = now)
                            },
                        )
                    }
                    SpaceItemType.Note -> Unit
                }
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
    val todayStart = startOfDay(now, zoneId)
    val ahead = mutableListOf<Pair<Long, SpaceNextItem>>()
    val earlier = mutableListOf<Pair<Long, SpaceNextItem>>()
    tasks.filter { it.status == TaskStatus.Open && it.spaceId != null }.forEach { task ->
        when {
            task.dueAt != null -> if (task.dueAt >= now) {
                ahead += task.spaceId!! to SpaceNextItem(task.title, task.dueAt)
            } else {
                earlier += task.spaceId!! to SpaceNextItem(task.title, task.dueAt, isEarlier = true)
            }
            task.scheduledDateEpochDay != null -> {
                val dayStart = java.time.LocalDate.ofEpochDay(task.scheduledDateEpochDay)
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                if (dayStart >= todayStart) {
                    ahead += task.spaceId!! to SpaceNextItem(task.title, dayStart, hasTime = false)
                } else {
                    earlier += task.spaceId!! to SpaceNextItem(task.title, dayStart, hasTime = false, isEarlier = true)
                }
            }
        }
    }
    reminders.filter { it.completedAt == null && it.spaceId != null }.forEach {
        if (it.dueAt >= now) {
            ahead += it.spaceId!! to SpaceNextItem(it.title, it.dueAt)
        } else {
            earlier += it.spaceId!! to SpaceNextItem(it.title, it.dueAt, isEarlier = true)
        }
    }
    val next = ahead.groupBy({ it.first }, { it.second }).mapValues { (_, items) -> items.minBy { it.at } }
    val waiting = earlier.groupBy({ it.first }, { it.second }).mapValues { (_, items) -> items.minBy { it.at } }
    return waiting + next
}

/** Open tasks (not done or archived) and reminders not yet handled, per Space. Notes are not counted. */
internal fun calculateSpaceOpenCounts(
    spaces: List<SpaceEntity>,
    tasks: List<TaskEntity>,
    reminders: List<ReminderEntity>,
): Map<Long, Int> {
    val counts = spaces.associate { it.id to 0 }.toMutableMap()
    tasks.filter { it.status != TaskStatus.Done && it.status != TaskStatus.Archived }
        .forEach { task -> task.spaceId?.let { id -> counts.computeIfPresent(id) { _, n -> n + 1 } } }
    reminders.filter { it.completedAt == null }
        .forEach { reminder -> reminder.spaceId?.let { id -> counts.computeIfPresent(id) { _, n -> n + 1 } } }
    return counts
}

/** Open tasks and unhandled reminders that fall on today, in any Space or none. */
internal fun calculateTodayCount(
    tasks: List<TaskEntity>,
    reminders: List<ReminderEntity>,
    now: Long,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): Int {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    fun isToday(at: Long) = java.time.Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate() == today
    val openTasks = tasks.count { task ->
        task.status == TaskStatus.Open && (
            (task.dueAt != null && isToday(task.dueAt)) ||
                (task.dueAt == null && task.scheduledDateEpochDay == today.toEpochDay())
            )
    }
    val openReminders = reminders.count { it.completedAt == null && isToday(it.dueAt) }
    return openTasks + openReminders
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
