package com.orbit.app.ui.screens.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpaceContents(
    val notes: List<NoteEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val captures: List<CaptureEntity> = emptyList(),
) {
    val size: Int get() = notes.size + tasks.size + reminders.size
}

data class SpacesUiState(
    val spaces: List<SpaceEntity> = emptyList(),
    val visibleSpaces: List<SpaceEntity> = emptyList(),
    val archivedSpaces: List<SpaceEntity> = emptyList(),
    val hiddenSpaces: List<SpaceEntity> = emptyList(),
    val selectedSpace: SpaceEntity? = null,
    val selectedContents: SpaceContents = SpaceContents(),
    val itemCounts: Map<Long, Int> = emptyMap(),
)

enum class SpaceItemType { Note, Task, Reminder, Capture }

data class SpaceItemReference(
    val type: SpaceItemType,
    val id: Long,
)

private data class AllSpaceContents(
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val captures: List<CaptureEntity>,
)

class SpacesViewModel(private val container: OrbitContainer) : ViewModel() {
    private val selectedSpaceId = MutableStateFlow<Long?>(null)

    private val allContents = combine(
        container.noteRepository.observeAll(),
        container.taskRepository.observeAll(),
        container.reminderRepository.observeAll(),
        container.captureRepository.observeAll(),
    ) { notes, tasks, reminders, captures ->
        AllSpaceContents(notes, tasks, reminders, captures)
    }

    val uiState = combine(
        container.spaceRepository.observeAll(),
        allContents,
        selectedSpaceId,
    ) { spaces, contents, selectedId ->
        val (visibleSpaces, archivedSpaces, hiddenSpaces) = partitionSpaces(spaces)
        SpacesUiState(
            spaces = spaces,
            visibleSpaces = visibleSpaces,
            archivedSpaces = archivedSpaces,
            hiddenSpaces = hiddenSpaces,
            selectedSpace = spaces.firstOrNull { it.id == selectedId },
            selectedContents = SpaceContents(
                notes = contents.notes.filter { it.spaceId == selectedId && !it.archived },
                tasks = contents.tasks.filter {
                    it.spaceId == selectedId && it.status != TaskStatus.Archived
                },
                reminders = contents.reminders.filter { it.spaceId == selectedId },
                captures = emptyList(),
            ),
            itemCounts = calculateSpaceItemCounts(
                spaces = spaces,
                notes = contents.notes,
                tasks = contents.tasks,
                reminders = contents.reminders,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SpacesUiState(),
    )

    fun selectSpace(spaceId: Long?) {
        selectedSpaceId.value = spaceId
    }

    fun createSpace(name: String, icon: String, colorAccent: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val nextOrder = (uiState.value.spaces.maxOfOrNull { it.sortOrder } ?: -1) + 1
            container.spaceRepository.insert(
                SpaceEntity(
                    name = cleanName,
                    icon = icon,
                    colorAccent = colorAccent,
                    sortOrder = nextOrder,
                ),
            )
        }
    }

    fun updateSpace(spaceId: Long, name: String, icon: String, colorAccent: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
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
            val now = System.currentTimeMillis()
            container.spaceRepository.update(
                current.copy(sortOrder = target.sortOrder, updatedAt = now),
            )
            container.spaceRepository.update(
                target.copy(sortOrder = current.sortOrder, updatedAt = now),
            )
        }
    }

    fun moveItem(item: SpaceItemReference, targetSpaceId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (item.type) {
                SpaceItemType.Note -> container.noteRepository.getById(item.id)?.let {
                    container.noteRepository.update(it.copy(spaceId = targetSpaceId, updatedAt = now))
                }

                SpaceItemType.Task -> container.taskRepository.getById(item.id)?.let {
                    container.taskRepository.update(it.copy(spaceId = targetSpaceId, updatedAt = now))
                }

                SpaceItemType.Reminder -> container.reminderRepository.getById(item.id)?.let {
                    container.reminderRepository.update(it.copy(spaceId = targetSpaceId, updatedAt = now))
                }

                SpaceItemType.Capture -> container.captureRepository.getById(item.id)?.let {
                    container.captureRepository.update(
                        it.copy(suggestedSpaceId = targetSpaceId, updatedAt = now),
                    )
                }
            }
        }
    }

    private fun updateStoredSpace(
        spaceId: Long,
        transform: (SpaceEntity) -> SpaceEntity,
    ) {
        viewModelScope.launch {
            container.spaceRepository.getById(spaceId)?.let { stored ->
                container.spaceRepository.update(transform(stored))
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
    reminders.forEach { increment(it.spaceId) }
    return counts
}
