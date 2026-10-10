package com.orbit.app.domain.capture

import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.domain.analyzer.BrainDumpSplitter
import com.orbit.app.domain.analyzer.CaptureAnalyzer
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource

/**
 * Turns one saved thought into a Brain Dump, but only when the user asked to split it
 * ("Looks like 3 thoughts · Split"). Gemini, when on, only says where to cut, and its
 * parts must be exact copies of the user's words; otherwise only safe local signs are
 * used. Nothing is saved as a note, task or reminder here.
 */
class ThoughtSplitter(
    private val captureRepository: CaptureRepository,
    private val brainDumpRepository: BrainDumpRepository,
    private val suggestionDao: CaptureSuggestionDao,
    private val analyzer: CaptureAnalyzer,
    private val geminiParts: suspend (String) -> List<String>?,
    private val geminiReady: suspend () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Result { Split, AlreadySplit, OneThought, Unavailable }

    /**
     * How many thoughts a one-line capture seems to hold (0 = one). Safe local signs
     * always count; the looser "several actions" guess is offered only when Gemini is
     * on, because only Gemini may cut on "and" and commas.
     */
    suspend fun possibleThoughts(rawText: String): Int {
        val local = analyzer.oneLineParts(rawText).size
        if (local >= 2) return local
        if (!analyzer.mightHoldSeveralThoughts(rawText) || !geminiReady()) return 0
        return BrainDumpSplitter.actionChunks(rawText).size.coerceAtLeast(2)
    }

    suspend fun split(captureId: Long): Result {
        val capture = captureRepository.getById(captureId) ?: return Result.Unavailable
        if (capture.status != CaptureStatus.Inbox) return Result.Unavailable
        if (brainDumpRepository.getSession(captureId) != null) return Result.AlreadySplit

        val fromGemini = runCatching { geminiParts(capture.rawText) }.getOrNull()?.takeIf { it.size >= 2 }
        val parts = fromGemini ?: analyzer.oneLineParts(capture.rawText).takeIf { it.size >= 2 }
            ?: return Result.OneThought
        val items = analyzer.brainDumpItemsFor(parts)
        if (items.size < 2) return Result.OneThought

        val timestamp = now()
        brainDumpRepository.createSession(
            session = BrainDumpSessionEntity(
                captureId = captureId,
                analyzerSource = (if (fromGemini != null) CaptureAnalyzerSource.Gemini else CaptureAnalyzerSource.Local).name,
                calendarDateContextEpochDay = suggestionDao.getByCaptureId(captureId)?.contextDateEpochDay,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
            items = items.mapIndexed { index, item -> item.toEntity(captureId, index + 1, timestamp) },
        )
        return Result.Split
    }
}
