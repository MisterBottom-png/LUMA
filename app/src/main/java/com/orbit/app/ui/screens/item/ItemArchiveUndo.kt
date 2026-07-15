package com.orbit.app.ui.screens.item

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.ui.navigation.ItemDetailType

internal sealed interface ArchiveOutcome {
    data class Archived(val operationId: Long) : ArchiveOutcome
    data object Ignored : ArchiveOutcome
    data object Missing : ArchiveOutcome
    data object Unsupported : ArchiveOutcome
}

internal enum class UndoOutcome {
    Restored,
    Ignored,
    Stale,
}

private sealed interface ArchivedSnapshot {
    data class Note(val item: NoteEntity) : ArchivedSnapshot
    data class Task(val item: TaskEntity) : ArchivedSnapshot
    data class Capture(val item: CaptureEntity) : ArchivedSnapshot
}

private data class PendingArchive(
    val operationId: Long,
    val snapshot: ArchivedSnapshot,
)

internal class ItemArchiveUndo(
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val captureRepository: CaptureRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private var nextOperationId = 1L
    private var pendingArchive: PendingArchive? = null
    private var operationInProgress = false

    suspend fun archive(type: ItemDetailType, itemId: Long): ArchiveOutcome {
        if (operationInProgress) return ArchiveOutcome.Ignored
        if (type == ItemDetailType.Reminder) return ArchiveOutcome.Unsupported

        operationInProgress = true
        return try {
            val now = currentTimeMillis()
            val snapshot = when (type) {
                ItemDetailType.Note -> {
                    val item = noteRepository.getById(itemId) ?: return ArchiveOutcome.Missing
                    if (item.archived) return ArchiveOutcome.Ignored
                    noteRepository.update(item.copy(archived = true, updatedAt = now))
                    ArchivedSnapshot.Note(item)
                }

                ItemDetailType.Task -> {
                    val item = taskRepository.getById(itemId) ?: return ArchiveOutcome.Missing
                    if (item.status == TaskStatus.Archived) return ArchiveOutcome.Ignored
                    taskRepository.update(item.copy(status = TaskStatus.Archived, updatedAt = now))
                    ArchivedSnapshot.Task(item)
                }

                ItemDetailType.Capture -> {
                    val item = captureRepository.getById(itemId) ?: return ArchiveOutcome.Missing
                    if (item.status == CaptureStatus.Archived) return ArchiveOutcome.Ignored
                    captureRepository.update(item.copy(status = CaptureStatus.Archived, updatedAt = now))
                    ArchivedSnapshot.Capture(item)
                }

                ItemDetailType.Reminder -> return ArchiveOutcome.Unsupported
            }
            val operationId = nextOperationId++
            pendingArchive = PendingArchive(operationId, snapshot)
            ArchiveOutcome.Archived(operationId)
        } catch (failure: Throwable) {
            pendingArchive = null
            throw failure
        } finally {
            operationInProgress = false
        }
    }

    suspend fun undo(operationId: Long): UndoOutcome {
        if (operationInProgress) return UndoOutcome.Ignored
        val pending = pendingArchive ?: return UndoOutcome.Stale
        if (pending.operationId != operationId) return UndoOutcome.Stale

        operationInProgress = true
        return try {
            when (val snapshot = pending.snapshot) {
                is ArchivedSnapshot.Note -> noteRepository.update(snapshot.item)
                is ArchivedSnapshot.Task -> taskRepository.update(snapshot.item)
                is ArchivedSnapshot.Capture -> captureRepository.update(snapshot.item)
            }
            pendingArchive = null
            UndoOutcome.Restored
        } catch (failure: Throwable) {
            pendingArchive = null
            throw failure
        } finally {
            operationInProgress = false
        }
    }

    fun expire(operationId: Long) {
        if (pendingArchive?.operationId == operationId && !operationInProgress) {
            pendingArchive = null
        }
    }
}
