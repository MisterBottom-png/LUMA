package com.orbit.app.ui.screens.spaces

import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository

sealed interface SpaceItemMoveOutcome {
    data class Moved(val undo: SpaceMoveUndo) : SpaceItemMoveOutcome
    data object Unchanged : SpaceItemMoveOutcome
    data object Missing : SpaceItemMoveOutcome
    data object Failed : SpaceItemMoveOutcome
}

/** Applies a Space move and keeps only the exact previous assignment needed for Undo. */
class SpaceItemMoveActions(
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun move(item: SpaceItemReference, targetSpaceId: Long?): SpaceItemMoveOutcome =
        runCatching { moveInternal(item, targetSpaceId) }.getOrDefault(SpaceItemMoveOutcome.Failed)

    private suspend fun moveInternal(
        item: SpaceItemReference,
        targetSpaceId: Long?,
    ): SpaceItemMoveOutcome = when (item.type) {
        SpaceItemType.Note -> noteRepository.getById(item.id)?.let { note ->
            moveIfChanged(item, note.spaceId, targetSpaceId) {
                noteRepository.update(note.copy(spaceId = targetSpaceId, updatedAt = currentTimeMillis()))
            }
        } ?: SpaceItemMoveOutcome.Missing

        SpaceItemType.Task -> taskRepository.getById(item.id)?.let { task ->
            moveIfChanged(item, task.spaceId, targetSpaceId) {
                taskRepository.update(task.copy(spaceId = targetSpaceId, updatedAt = currentTimeMillis()))
            }
        } ?: SpaceItemMoveOutcome.Missing

        SpaceItemType.Reminder -> reminderRepository.getById(item.id)?.let { reminder ->
            moveIfChanged(item, reminder.spaceId, targetSpaceId) {
                reminderRepository.update(reminder.copy(spaceId = targetSpaceId, updatedAt = currentTimeMillis()))
            }
        } ?: SpaceItemMoveOutcome.Missing
    }

    suspend fun restore(undo: SpaceMoveUndo): Boolean = when (undo.item.type) {
        SpaceItemType.Note -> noteRepository.getById(undo.item.id)?.let { note ->
            noteRepository.update(note.copy(spaceId = undo.previousSpaceId, updatedAt = currentTimeMillis()))
            true
        } ?: false

        SpaceItemType.Task -> taskRepository.getById(undo.item.id)?.let { task ->
            taskRepository.update(task.copy(spaceId = undo.previousSpaceId, updatedAt = currentTimeMillis()))
            true
        } ?: false

        SpaceItemType.Reminder -> reminderRepository.getById(undo.item.id)?.let { reminder ->
            reminderRepository.update(reminder.copy(spaceId = undo.previousSpaceId, updatedAt = currentTimeMillis()))
            true
        } ?: false
    }

    private suspend fun moveIfChanged(
        item: SpaceItemReference,
        previousSpaceId: Long?,
        targetSpaceId: Long?,
        update: suspend () -> Unit,
    ): SpaceItemMoveOutcome {
        if (previousSpaceId == targetSpaceId) return SpaceItemMoveOutcome.Unchanged
        update()
        return SpaceItemMoveOutcome.Moved(SpaceMoveUndo(item, previousSpaceId))
    }
}
