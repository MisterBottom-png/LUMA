package com.orbit.app.ui.screens.item

import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.ui.navigation.ItemDetailType

sealed interface ItemSchedule {
    data object Unscheduled : ItemSchedule
    data class DateOnly(val epochDay: Long) : ItemSchedule
    data class Timed(val epochMillis: Long) : ItemSchedule
}

sealed interface ScheduleOutcome {
    data class Applied(val operationId: Long) : ScheduleOutcome
    data object Ignored : ScheduleOutcome
    data object Missing : ScheduleOutcome
    data object Unsupported : ScheduleOutcome
}

enum class ScheduleUndoOutcome {
    Restored,
    Stale,
    Missing,
}

class ItemScheduleActions(
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private var nextOperationId = 1L
    private var pendingUndo: PendingUndo? = null

    suspend fun apply(type: ItemDetailType, itemId: Long, schedule: ItemSchedule): ScheduleOutcome {
        requireScheduleValue(schedule)
        val previous = when (type) {
            ItemDetailType.Note -> {
                val note = noteRepository.getById(itemId) ?: return ScheduleOutcome.Missing
                val current = scheduleOf(note.scheduledDateEpochDay, note.scheduledAt)
                if (current == schedule) return ScheduleOutcome.Ignored
                noteRepository.update(
                    note.copy(
                        scheduledDateEpochDay = schedule.epochDayOrNull(),
                        scheduledAt = schedule.epochMillisOrNull(),
                        updatedAt = currentTimeMillis(),
                    ),
                )
                current
            }

            ItemDetailType.Task -> {
                val task = taskRepository.getById(itemId) ?: return ScheduleOutcome.Missing
                val current = scheduleOf(task.scheduledDateEpochDay, task.dueAt)
                if (current == schedule) return ScheduleOutcome.Ignored
                taskRepository.update(
                    task.copy(
                        scheduledDateEpochDay = schedule.epochDayOrNull(),
                        dueAt = schedule.epochMillisOrNull(),
                        updatedAt = currentTimeMillis(),
                    ),
                )
                current
            }

            ItemDetailType.Capture,
            ItemDetailType.Reminder,
            -> return ScheduleOutcome.Unsupported
        }
        val operationId = nextOperationId++
        pendingUndo = PendingUndo(operationId, type, itemId, previous)
        return ScheduleOutcome.Applied(operationId)
    }

    suspend fun undo(operationId: Long): ScheduleUndoOutcome {
        val pending = pendingUndo?.takeIf { it.operationId == operationId }
            ?: return ScheduleUndoOutcome.Stale
        pendingUndo = null
        return when (pending.type) {
            ItemDetailType.Note -> {
                val note = noteRepository.getById(pending.itemId) ?: return ScheduleUndoOutcome.Missing
                noteRepository.update(
                    note.copy(
                        scheduledDateEpochDay = pending.previous.epochDayOrNull(),
                        scheduledAt = pending.previous.epochMillisOrNull(),
                        updatedAt = currentTimeMillis(),
                    ),
                )
                ScheduleUndoOutcome.Restored
            }

            ItemDetailType.Task -> {
                val task = taskRepository.getById(pending.itemId) ?: return ScheduleUndoOutcome.Missing
                taskRepository.update(
                    task.copy(
                        scheduledDateEpochDay = pending.previous.epochDayOrNull(),
                        dueAt = pending.previous.epochMillisOrNull(),
                        updatedAt = currentTimeMillis(),
                    ),
                )
                ScheduleUndoOutcome.Restored
            }

            ItemDetailType.Capture,
            ItemDetailType.Reminder,
            -> ScheduleUndoOutcome.Stale
        }
    }

    fun expire(operationId: Long) {
        if (pendingUndo?.operationId == operationId) pendingUndo = null
    }

    private data class PendingUndo(
        val operationId: Long,
        val type: ItemDetailType,
        val itemId: Long,
        val previous: ItemSchedule,
    )
}

private fun requireScheduleValue(schedule: ItemSchedule) {
    when (schedule) {
        ItemSchedule.Unscheduled -> Unit
        is ItemSchedule.DateOnly -> require(schedule.epochDay in -365_243_219_162L..365_241_780_471L) {
            "Invalid schedule date"
        }
        is ItemSchedule.Timed -> require(schedule.epochMillis > 0L) { "Invalid schedule time" }
    }
}

private fun scheduleOf(epochDay: Long?, epochMillis: Long?): ItemSchedule = when {
    epochDay != null && epochMillis == null -> ItemSchedule.DateOnly(epochDay)
    epochDay == null && epochMillis != null -> ItemSchedule.Timed(epochMillis)
    else -> ItemSchedule.Unscheduled
}

private fun ItemSchedule.epochDayOrNull(): Long? = (this as? ItemSchedule.DateOnly)?.epochDay

private fun ItemSchedule.epochMillisOrNull(): Long? = (this as? ItemSchedule.Timed)?.epochMillis
