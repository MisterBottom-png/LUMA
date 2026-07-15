package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConfirmCaptureActionUseCase(
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
) {
    private val finalizationMutex = Mutex()

    suspend fun saveNote(
        captureId: Long,
        spaceId: Long?,
        title: String,
        scheduledDateEpochDay: Long? = null,
    ): Long =
        finalizationMutex.withLock {
            val capture = requireInboxCapture(captureId)
            val noteId = noteRepository.insert(
                NoteEntity(
                    title = title.ifBlank { capture.rawText.toNoteTitle() },
                    body = capture.rawText,
                    spaceId = spaceId,
                    scheduledDateEpochDay = scheduledDateEpochDay,
                ),
            )
            try {
                markProcessed(capture, noteId)
            } catch (exception: Exception) {
                noteRepository.deleteById(noteId)
                throw exception
            }
            noteId
        }

    suspend fun createTask(
        captureId: Long,
        spaceId: Long?,
        title: String,
        dueAt: Long?,
        scheduledDateEpochDay: Long? = null,
        status: TaskStatus = TaskStatus.Open,
    ): Long = finalizationMutex.withLock {
        val capture = requireInboxCapture(captureId)
        val taskTitle = title.trim().ifBlank { capture.rawText }
        val taskId = taskRepository.insert(
            TaskEntity(
                title = taskTitle,
                notes = capture.rawText.takeUnless { it == taskTitle }.orEmpty(),
                spaceId = spaceId,
                status = status,
                dueAt = dueAt,
                scheduledDateEpochDay = scheduledDateEpochDay,
            ),
        )
        try {
            markProcessed(capture, taskId)
        } catch (exception: Exception) {
            taskRepository.deleteById(taskId)
            throw exception
        }
        taskId
    }

    suspend fun createReminder(
        captureId: Long,
        spaceId: Long?,
        title: String,
        dueAt: Long,
        linkedTaskId: Long? = null,
    ): Long = finalizationMutex.withLock {
        val capture = requireInboxCapture(captureId)
        val reminderId = reminderRepository.insert(
            ReminderEntity(
                title = title.trim().ifBlank { capture.rawText },
                dueAt = dueAt,
                spaceId = spaceId,
                linkedTaskId = linkedTaskId,
                linkedCaptureId = capture.id,
            ),
        )
        try {
            markProcessed(capture, reminderId)
        } catch (exception: Exception) {
            reminderRepository.deleteById(reminderId)
            throw exception
        }
        reminderId
    }

    suspend fun saveBrainDumpNote(
        title: String,
        body: String,
        spaceId: Long?,
    ): Long = noteRepository.insert(
        NoteEntity(
            title = title.trim().ifBlank { body.toNoteTitle() },
            body = body,
            spaceId = spaceId,
        ),
    )

    suspend fun saveBrainDumpTask(
        title: String,
        notes: String,
        spaceId: Long?,
    ): Long = taskRepository.insert(
        TaskEntity(
            title = title.trim().ifBlank { "Untitled task" },
            notes = notes,
            spaceId = spaceId,
        ),
    )

    suspend fun markCaptureReviewed(captureId: Long) {
        captureRepository.getById(captureId)?.let { capture ->
            if (capture.status != CaptureStatus.Archived) {
                captureRepository.update(
                    capture.copy(
                        status = CaptureStatus.Processed,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun requireInboxCapture(captureId: Long): CaptureEntity {
        val capture = requireNotNull(captureRepository.getById(captureId)) {
            "Capture $captureId no longer exists"
        }
        check(capture.status == CaptureStatus.Inbox) {
            "Capture $captureId has already been processed"
        }
        return capture
    }

    private suspend fun markProcessed(capture: CaptureEntity, linkedItemId: Long) {
        captureRepository.update(
            capture.copy(
                status = CaptureStatus.Processed,
                linkedItemId = linkedItemId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun String.toNoteTitle(): String =
        lineSequence().firstOrNull().orEmpty().trim().ifBlank { "Untitled note" }.take(80)
}
