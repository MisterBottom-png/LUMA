package com.orbit.app.domain.capture

import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpReminderStatus
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureSource
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.domain.usecase.CaptureFinalizationTransaction
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Produces a suggestion for one capture (local rules, or Gemini when the user enabled it). */
fun interface CaptureSuggester {
    suspend fun suggest(rawText: String, allowedSpaces: List<String>): CaptureAnalysis
}

sealed interface CaptureInboxEvent {
    val captureId: Long

    /** The capture now has a stored suggestion (or a Brain Dump session). */
    data class Analyzed(override val captureId: Long, val analysis: CaptureAnalysis) : CaptureInboxEvent

    /** Analysis failed; the capture is still safe in the Inbox, just without a suggestion. */
    data class AnalysisFailed(override val captureId: Long) : CaptureInboxEvent
}

/**
 * The capture model: a thought is saved first, analysed afterwards, and its
 * suggestion is stored next to it. Nothing here creates a note, task or reminder;
 * that only happens when the user confirms in To sort (or in the optional sheet).
 *
 * Analysis runs on an application scope so it finishes even when the user leaves
 * Home; captures left without a suggestion (for example after a force-stop) are
 * picked up again by [analyzePending].
 */
class CaptureInbox(
    private val captureRepository: CaptureRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val brainDumpRepository: BrainDumpRepository,
    private val spaceRepository: SpaceRepository,
    private val suggester: CaptureSuggester,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Wraps the final "is it still in To sort? then store" step, so a result that
     * arrives after the user sorted, archived or changed the thought cannot undo that.
     */
    private val transaction: CaptureFinalizationTransaction = DirectTransaction,
) {
    private val analysisMutex = Mutex()
    private val inFlight = mutableSetOf<Long>()
    private val _events = MutableSharedFlow<CaptureInboxEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CaptureInboxEvent> = _events.asSharedFlow()

    /**
     * Saves the raw thought in the Inbox and returns its id. The thought is safe
     * once this returns; analysis continues in the background. [onSaved] runs with
     * the id before analysis starts, so a caller waiting for this capture's
     * [events] cannot miss a fast result.
     */
    suspend fun save(
        rawText: String,
        source: CaptureSource = CaptureSource.Manual,
        contextDateEpochDay: Long? = null,
        spaceId: Long? = null,
        onSaved: (Long) -> Unit = {},
    ): Long {
        val text = rawText.trim()
        require(text.isNotEmpty()) { "A capture cannot be blank" }
        val timestamp = now()
        val id = captureRepository.insert(
            CaptureEntity(
                rawText = text,
                createdAt = timestamp,
                updatedAt = timestamp,
                status = CaptureStatus.Inbox,
                source = source,
                // Kept on the thought, so analysis after a restart still knows the day.
                contextDateEpochDay = contextDateEpochDay,
                // Chosen by the user (a Space's "+"); it wins over any suggested Space.
                suggestedSpaceId = spaceId,
            ),
        )
        onSaved(id)
        scope.launch { analyze(id) }
        return id
    }

    /**
     * Analyses one Inbox capture and stores the suggestion. Safe to call repeatedly:
     * a capture that already has a suggestion or Brain Dump session is left as is.
     * The Calendar day, if any, is read from the capture row.
     */
    suspend fun analyze(captureId: Long): CaptureAnalysis? {
        analysisMutex.withLock {
            if (!inFlight.add(captureId)) return null
        }
        try {
            val capture = captureRepository.getById(captureId) ?: return null
            if (capture.status != CaptureStatus.Inbox) return null
            if (suggestionDao.getByCaptureId(captureId) != null) return null
            if (brainDumpRepository.getSession(captureId) != null) return null

            val spaces = activeSpaces()
            val analysis = try {
                suggester.suggest(capture.rawText, spaces.map { it.name })
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _events.tryEmit(CaptureInboxEvent.AnalysisFailed(captureId))
                return null
            }
            // The suggester can take a while (Gemini waits up to its network timeout).
            // If the user acted on the thought meanwhile, the late result is dropped.
            if (!store(captureId, analysis, spaces)) return null
            _events.tryEmit(CaptureInboxEvent.Analyzed(captureId, analysis))
            return analysis
        } finally {
            analysisMutex.withLock { inFlight.remove(captureId) }
        }
    }

    /** Analyses every Inbox capture that has neither a suggestion nor a Brain Dump session. */
    suspend fun analyzePending(): Int {
        val withSuggestion = suggestionDao.getAll().mapTo(hashSetOf()) { it.captureId }
        val withSession = brainDumpRepository.getAllSessions().mapTo(hashSetOf()) { it.session.captureId }
        val pending = captureRepository.observeAll().first()
            .filter { it.status == CaptureStatus.Inbox && it.id !in withSuggestion && it.id !in withSession }
        pending.forEach { analyze(it.id) }
        return pending.size
    }

    /** Hides LUMA's suggestion without touching the thought itself. */
    suspend fun dismissSuggestion(captureId: Long) {
        suggestionDao.setDismissed(captureId, dismissed = true, updatedAt = now())
    }

    suspend fun restoreSuggestion(captureId: Long) {
        suggestionDao.setDismissed(captureId, dismissed = false, updatedAt = now())
    }

    /**
     * Stores the suggestion only if the thought is still waiting in To sort, re-read
     * inside one transaction. Returns false when the user already sorted, archived or
     * otherwise moved it on, or when another path stored a suggestion first.
     *
     * The capture row is re-read here rather than reusing the copy read before
     * analysis: writing that older copy back would restore a stale status and
     * linked item, and drop a Space the user picked in the meantime.
     */
    private suspend fun store(
        captureId: Long,
        analysis: CaptureAnalysis,
        spaces: List<SpaceEntity>,
    ): Boolean = transaction.run storeIfStillWaiting@{
        val current = captureRepository.getById(captureId) ?: return@storeIfStillWaiting false
        val contextDateEpochDay = current.contextDateEpochDay
        if (current.status != CaptureStatus.Inbox) return@storeIfStillWaiting false
        if (suggestionDao.getByCaptureId(captureId) != null) return@storeIfStillWaiting false
        if (brainDumpRepository.getSession(captureId) != null) return@storeIfStillWaiting false

        val timestamp = now()
        val analysedSpace = spaces.firstOrNull { it.name.equals(analysis.suggestedSpaceName, ignoreCase = true) }
            ?.takeUnless { analysis.confidenceLevel == CaptureConfidence.Low }
        // A Space the user already chose for this thought wins over the analysis, both
        // on the thought and in the stored suggestion that sorting reads.
        val chosenSpace = current.suggestedSpaceId?.let { chosenId ->
            spaces.firstOrNull { it.id == chosenId } ?: spaceRepository.getById(chosenId)
        }
        val space = chosenSpace ?: analysedSpace
        if (analysis.brainDumpItems.isNotEmpty()) {
            brainDumpRepository.createSession(
                session = BrainDumpSessionEntity(
                    captureId = captureId,
                    analyzerSource = analysis.analyzerSource.name,
                    calendarDateContextEpochDay = contextDateEpochDay,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
                items = analysis.brainDumpItems.mapIndexed { index, item ->
                    item.toEntity(captureId, index + 1, timestamp)
                },
            )
        }
        suggestionDao.upsert(analysis.toSuggestionEntity(captureId, space?.name, contextDateEpochDay, timestamp))
        captureRepository.update(
            current.copy(
                suggestedType = analysis.suggestedType,
                suggestedSpaceId = current.suggestedSpaceId ?: space?.id,
            ),
        )
        true
    }

    private suspend fun activeSpaces(): List<SpaceEntity> =
        spaceRepository.observeAll().first()
            .filterNot { it.hidden || it.archived }
            .sortedBy { it.sortOrder }
}

/** No database transaction; for callers (and tests) that do not need one. */
internal object DirectTransaction : CaptureFinalizationTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

internal fun CaptureAnalysis.toSuggestionEntity(
    captureId: Long,
    spaceName: String?,
    contextDateEpochDay: Long?,
    timestamp: Long,
): CaptureSuggestionEntity {
    val fromGemini = analyzerSource == CaptureAnalyzerSource.Gemini
    return CaptureSuggestionEntity(
        captureId = captureId,
        suggestedType = suggestedType,
        suggestedTitle = suggestedTitle.ifBlank { rawText.lineSequence().first().take(80) },
        suggestedSpaceName = spaceName,
        suggestedLabels = suggestedLabels.joinToString("\n"),
        suggestedDueAt = null,
        suggestedReminderAt = suggestedReminderAt,
        reminderTimeStatus = reminderTimeStatus.name,
        reminderPhrase = reminderPhrase,
        lifeSignal = lifeSignal.name,
        confidence = confidence,
        analyzerSource = analyzerSource.name,
        // Local reasons are rebuilt from resources at display time, so they follow
        // the current language; Gemini wrote its reasons in the user's language.
        typeReason = if (fromGemini) typeReason else "",
        spaceReason = if (fromGemini) spaceReason else "",
        nextAction = if (fromGemini) suggestedNextAction else "",
        // For a task this is its day: the day named in the thought, else the Calendar day.
        contextDateEpochDay = taskDateEpochDay.takeIf { suggestedType.isTaskLike() } ?: contextDateEpochDay,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

internal fun BrainDumpSuggestion.toEntity(
    captureId: Long,
    ordinal: Int,
    timestamp: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
) = BrainDumpItemEntity(
    captureId = captureId,
    sourceKey = id,
    ordinal = ordinal,
    rawText = rawText,
    suggestedTitle = title,
    suggestedType = suggestedType,
    suggestedSpaceName = suggestedSpaceName,
    confidence = confidence,
    tinyNextAction = tinyNextAction,
    reason = reason,
    reminderStatus = when (reminderTimeStatus) {
        ReminderTimeStatus.Unspecified -> BrainDumpReminderStatus.Unspecified
        ReminderTimeStatus.Resolved -> BrainDumpReminderStatus.Resolved
        ReminderTimeStatus.NeedsClarification -> BrainDumpReminderStatus.NeedsClarification
    },
    // Brain Dump rows have no date-only column; a task's day is kept as a placeholder
    // and read back as a day (see TaskDatePlaceholder).
    suggestedReminderAt = suggestedReminderAt
        ?: taskDateEpochDay?.takeIf { suggestedType.isTaskLike() }?.let { TaskDatePlaceholder.encode(it) },
    reminderPhrase = reminderPhrase,
    createdAt = timestamp,
    updatedAt = timestamp,
)
