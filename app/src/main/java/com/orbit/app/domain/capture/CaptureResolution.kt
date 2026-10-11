package com.orbit.app.domain.capture

import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.usecase.CaptureFinalizationTransaction
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.ReminderSaveOutcomes
import kotlinx.coroutines.flow.first

/**
 * Explicit, user-confirmed outcomes for a capture in To sort, and their Undo.
 * Every function here runs only after a user tap.
 */
class CaptureResolution(
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val spaceRepository: SpaceRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val confirmCaptureAction: ConfirmCaptureActionUseCase,
    private val transaction: CaptureFinalizationTransaction,
    private val reminderOutcomes: ReminderSaveOutcomes,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Creates the reminder from the quick question on Home and says what it achieved. */
    suspend fun createQuickReminder(captureId: Long, title: String, reminderAt: Long): ReminderSaveOutcome {
        val suggestion = suggestionDao.getByCaptureId(captureId)
        val reminderId = confirmCaptureAction.createReminder(
            captureId = captureId,
            spaceId = activeSpaceId(captureRepository.getById(captureId)?.suggestedSpaceId)
                ?: spaceIdFor(suggestion?.suggestedSpaceName),
            title = title,
            dueAt = reminderAt,
            labelNames = suggestion?.labelNames().orEmpty(),
        )
        return reminderOutcomes.of(reminderId)
    }

    /**
     * Accepts LUMA's stored suggestion as it is ("one tap"). Returns null when the
     * suggestion needs a choice first (for example a reminder without a time).
     */
    suspend fun acceptSuggestion(captureId: Long): AcceptedSuggestion? {
        val capture = captureRepository.getById(captureId) ?: return null
        if (capture.status != CaptureStatus.Inbox) return null
        val suggestion = suggestionDao.getByCaptureId(captureId)?.takeUnless { it.dismissed } ?: return null
        // Not sure enough for one tap: the user chooses in the sheet.
        if (suggestion.isLowConfidence) return null
        // A Space the user picked when saving wins over the suggested one.
        val spaceId = activeSpaceId(capture.suggestedSpaceId) ?: spaceIdFor(suggestion.suggestedSpaceName)
        val labels = suggestion.labelNames()
        val title = suggestion.suggestedTitle
        return when (suggestion.suggestedType) {
            SuggestedItemType.Reminder -> {
                val at = suggestion.suggestedReminderAt?.takeIf { it > now() } ?: return null
                val id = confirmCaptureAction.createReminder(captureId, spaceId, title, at, labelNames = labels)
                AcceptedSuggestion(SuggestedItemType.Reminder, id, reminderOutcomes.of(id))
            }
            SuggestedItemType.Task, SuggestedItemType.MondayItem -> {
                val id = confirmCaptureAction.createTask(
                    captureId = captureId,
                    spaceId = spaceId,
                    title = title,
                    dueAt = suggestion.suggestedDueAt,
                    scheduledDateEpochDay = suggestion.taskDateEpochDay().takeIf { suggestion.suggestedDueAt == null },
                    labelNames = labels,
                )
                AcceptedSuggestion(SuggestedItemType.Task, id)
            }
            SuggestedItemType.Note -> {
                val id = confirmCaptureAction.saveNote(
                    captureId = captureId,
                    spaceId = spaceId,
                    title = title,
                    scheduledDateEpochDay = suggestion.contextDateEpochDay,
                    labelNames = labels,
                )
                AcceptedSuggestion(SuggestedItemType.Note, id)
            }
        }
    }

    /** Undo for a just-sorted capture: removes the new item and returns the thought to To sort. */
    suspend fun undo(captureId: Long, itemType: SuggestedItemType, itemId: Long) {
        transaction.run {
            when (itemType) {
                SuggestedItemType.Note -> noteRepository.deleteById(itemId)
                SuggestedItemType.Task, SuggestedItemType.MondayItem -> taskRepository.deleteById(itemId)
                SuggestedItemType.Reminder -> reminderRepository.deleteById(itemId)
            }
            captureRepository.getById(captureId)?.let { capture ->
                captureRepository.update(
                    capture.copy(status = CaptureStatus.Inbox, linkedItemId = null, updatedAt = now()),
                )
            }
        }
    }

    /** "Let it go": the user explicitly archives a thought (Undo restores it). */
    suspend fun archive(captureId: Long) {
        val capture = captureRepository.getById(captureId) ?: return
        if (capture.status != CaptureStatus.Inbox) return
        captureRepository.update(capture.copy(status = CaptureStatus.Archived, updatedAt = now()))
    }

    suspend fun unarchive(captureId: Long) {
        val capture = captureRepository.getById(captureId) ?: return
        if (capture.status != CaptureStatus.Archived) return
        captureRepository.update(capture.copy(status = CaptureStatus.Inbox, updatedAt = now()))
    }

    private suspend fun activeSpaceId(id: Long?): Long? = id?.let { spaceId ->
        spaceRepository.getById(spaceId)?.takeUnless { it.archived }?.id
    }

    private suspend fun spaceIdFor(name: String?): Long? = name?.let { spaceName ->
        spaceRepository.observeAll().first()
            .firstOrNull { !it.archived && it.name.equals(spaceName, ignoreCase = true) }
            ?.id
    }
}

data class AcceptedSuggestion(
    val itemType: SuggestedItemType,
    val itemId: Long,
    /** Set for a reminder: whether it can actually reach the user. */
    val reminderOutcome: ReminderSaveOutcome? = null,
)
