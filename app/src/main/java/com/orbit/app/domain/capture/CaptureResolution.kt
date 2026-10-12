package com.orbit.app.domain.capture

import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
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
 * Outcomes for a capture in To sort, and their Undo. Each runs after a user tap,
 * except [acceptSuggestion], which the automatic filing of a clear thought also uses.
 * "Change" never comes here: it opens the item itself, so nothing is deleted before
 * the user confirms an edit.
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
        return reminderOutcomes.of(reminderId).also { recordReminderOutcome(captureId, reminderId, it) }
    }

    /**
     * Stores what saving [reminderId] achieved on its thought: `ReminderSet` when it can
     * ring, `NeedsYou` with the reason when it cannot. Every place that turns a thought
     * into a reminder calls this once it knows the [outcome].
     */
    suspend fun recordReminderOutcome(captureId: Long, reminderId: Long, outcome: ReminderSaveOutcome) {
        transaction.run {
            val capture = captureRepository.getById(captureId) ?: return@run
            // Undone or changed meanwhile: the thought no longer belongs to this reminder.
            if (capture.status != CaptureStatus.Processed || capture.linkedItemId != reminderId) return@run
            captureRepository.update(capture.withOutcome(outcome.toCaptureOutcome(), now()))
        }
    }

    /**
     * Accepts LUMA's stored suggestion as it is ("one tap"). Returns null when the
     * suggestion needs a choice first (for example a reminder without a time).
     *
     * [automatic] is set by [AutoFiler]: the thought is then filed only while no decision
     * was made about it, checked again inside the filing transaction.
     */
    suspend fun acceptSuggestion(captureId: Long, automatic: Boolean = false): AcceptedSuggestion? {
        val capture = captureRepository.getById(captureId) ?: return null
        if (capture.status != CaptureStatus.Inbox) return null
        if (automatic && capture.decidedAt != null) return null
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
                val id = confirmCaptureAction.createReminder(
                    captureId,
                    spaceId,
                    title,
                    at,
                    labelNames = labels,
                    onlyIfUndecided = automatic,
                )
                val outcome = reminderOutcomes.of(id)
                recordReminderOutcome(captureId, id, outcome)
                AcceptedSuggestion(SuggestedItemType.Reminder, id, outcome)
            }
            SuggestedItemType.Task, SuggestedItemType.MondayItem -> {
                val id = confirmCaptureAction.createTask(
                    captureId = captureId,
                    spaceId = spaceId,
                    title = title,
                    dueAt = suggestion.suggestedDueAt,
                    scheduledDateEpochDay = suggestion.taskDateEpochDay().takeIf { suggestion.suggestedDueAt == null },
                    status = suggestion.taskStatus(),
                    labelNames = labels,
                    onlyIfUndecided = automatic,
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
                    onlyIfUndecided = automatic,
                )
                AcceptedSuggestion(SuggestedItemType.Note, id)
            }
        }
    }

    /**
     * Undo for a just-sorted capture: removes the new item and returns the thought to
     * To sort as a plain saved thought. The user decided, so [decidedAt][com.orbit.app.data.local.entity.CaptureEntity.decidedAt]
     * is stamped and a late automatic decision cannot file it again.
     */
    suspend fun undo(captureId: Long, itemType: SuggestedItemType, itemId: Long) {
        transaction.run {
            // Only the item this thought still became may be removed. If it changed since
            // (its type was changed, or it was deleted), nothing is touched: putting the
            // thought back in To sort would let it be filed a second time.
            val capture = captureRepository.getById(captureId)
            val stillLinked = capture != null && capture.status == CaptureStatus.Processed &&
                capture.linkedItemId == itemId && itemExists(itemType, itemId)
            check(stillLinked) { "The sorted item changed since; Undo is no longer possible." }
            when (itemType) {
                SuggestedItemType.Note -> noteRepository.deleteById(itemId)
                SuggestedItemType.Task, SuggestedItemType.MondayItem -> taskRepository.deleteById(itemId)
                SuggestedItemType.Reminder -> reminderRepository.deleteById(itemId)
            }
            captureRepository.getById(captureId)?.let { capture ->
                val timestamp = now()
                captureRepository.update(
                    capture.copy(
                        status = CaptureStatus.Inbox,
                        linkedItemId = null,
                        updatedAt = timestamp,
                        outcome = CaptureOutcome.Saved,
                        outcomeReason = CaptureOutcomeReason.None,
                        outcomeAt = timestamp,
                        decidedAt = timestamp,
                    ),
                )
            }
        }
    }

    /**
     * "Just keep it": the thought stays a saved thought, in To sort and in Search, and
     * nothing can ring for it. A reminder made from it (one that cannot ring, or whose
     * time passed) is removed first through [undo], which cancels its alarm and backup
     * work. A thought filed as a note or task is left as it is.
     */
    suspend fun keep(captureId: Long) {
        val capture = captureRepository.getById(captureId) ?: return
        val linkedId = capture.linkedItemId
        if (capture.status == CaptureStatus.Processed && linkedId != null) {
            val reminder = reminderRepository.getById(linkedId) ?: return
            if (capture.isSortedInto(reminder)) undo(captureId, SuggestedItemType.Reminder, linkedId)
            return
        }
        transaction.run {
            val current = captureRepository.getById(captureId)?.takeIf { it.status == CaptureStatus.Inbox } ?: return@run
            val timestamp = now()
            captureRepository.update(
                current.copy(
                    outcome = CaptureOutcome.Saved,
                    outcomeReason = CaptureOutcomeReason.None,
                    outcomeAt = timestamp,
                    decidedAt = timestamp,
                ),
            )
        }
    }

    /** "Let it go": the user explicitly archives a thought (Undo restores it). Its outcome is kept. */
    suspend fun archive(captureId: Long) {
        val capture = captureRepository.getById(captureId) ?: return
        if (capture.status != CaptureStatus.Inbox) return
        val timestamp = now()
        captureRepository.update(
            capture.copy(status = CaptureStatus.Archived, updatedAt = timestamp, decidedAt = timestamp),
        )
    }

    suspend fun unarchive(captureId: Long) {
        val capture = captureRepository.getById(captureId) ?: return
        if (capture.status != CaptureStatus.Archived) return
        captureRepository.update(capture.copy(status = CaptureStatus.Inbox, updatedAt = now()))
    }

    private suspend fun itemExists(itemType: SuggestedItemType, itemId: Long): Boolean = when (itemType) {
        SuggestedItemType.Note -> noteRepository.getById(itemId) != null
        SuggestedItemType.Task, SuggestedItemType.MondayItem -> taskRepository.getById(itemId) != null
        SuggestedItemType.Reminder -> reminderRepository.getById(itemId) != null
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
