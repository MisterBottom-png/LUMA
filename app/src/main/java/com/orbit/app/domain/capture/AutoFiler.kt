package com.orbit.app.domain.capture

import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.domain.usecase.CaptureFinalizationTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Applies [decide] to analysed thoughts. A pending decision is a row, not a coroutine:
 * an Inbox thought with a stored suggestion and `decidedAt == null` has not been
 * decided yet, so [applyPending] after any restart finishes what was interrupted.
 *
 * - Filing goes through [CaptureResolution.acceptSuggestion], whose transaction refuses
 *   a thought that already left the Inbox, so two runs racing for one thought make one
 *   item; the loser's exception is ignored.
 * - Only rows with `decidedAt == null` are touched, so Undo, archive and every other
 *   user action (which stamp `decidedAt`) always win over a late run.
 * - No learning event is recorded: an automatic filing is not the user agreeing.
 * - Nothing here retries; the next app start is the retry.
 */
class AutoFiler(
    private val captureRepository: CaptureRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val brainDumpRepository: BrainDumpRepository,
    private val captureResolution: CaptureResolution,
    private val transaction: CaptureFinalizationTransaction,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Decides every analysed thought still waiting for a decision. Returns how many were decided. */
    suspend fun applyPending(): Int {
        val pending = captureRepository.observeAll().first().filter { it.isPending() }
        return pending.count { apply(it.id) }
    }

    /** Decides one thought if it is still pending. Returns true when this call decided it. */
    suspend fun apply(captureId: Long): Boolean {
        val capture = captureRepository.getById(captureId)?.takeIf { it.isPending() } ?: return false
        val suggestion = suggestionDao.getByCaptureId(captureId)?.takeUnless { it.dismissed } ?: return false
        val decision = if (brainDumpRepository.getSession(captureId) != null) {
            CaptureDecision.Keep
        } else {
            val hasSpace = capture.suggestedSpaceId != null || suggestion.suggestedSpaceName != null
            decide(suggestion.toAnalysis(capture.rawText), hasSpace, now())
        }
        return when (decision) {
            is CaptureDecision.Act -> act(captureId, decision.type)
            is CaptureDecision.Ask -> record(captureId, StoredCaptureOutcome(CaptureOutcome.NeedsYou, decision.reason))
            CaptureDecision.Keep -> record(captureId, stored = null)
        }
    }

    private suspend fun act(captureId: Long, type: SuggestedItemType): Boolean {
        val accepted = try {
            captureResolution.acceptSuggestion(captureId, automatic = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Another run (or the user) sorted it first.
            return false
        }
        if (accepted != null) return true
        // Not filed after all: a reminder whose time passed meanwhile is a question,
        // anything else is kept.
        val fallback = if (type == SuggestedItemType.Reminder) {
            StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimePassed)
        } else {
            null
        }
        return record(captureId, fallback)
    }

    /** Stamps the decision (and [stored], when given) only if the thought is still pending. */
    private suspend fun record(captureId: Long, stored: StoredCaptureOutcome?): Boolean = transaction.run {
        val current = captureRepository.getById(captureId)?.takeIf { it.isPending() } ?: return@run false
        val timestamp = now()
        captureRepository.update(
            if (stored != null) current.withOutcome(stored, timestamp) else current.copy(decidedAt = timestamp),
        )
        true
    }

    private fun CaptureEntity.isPending(): Boolean = status == CaptureStatus.Inbox && decidedAt == null
}
