package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.AiSuggestionHistoryRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordAiLearningEventUseCaseTest {
    @Test
    fun acceptedSuggestionStoresHistoryWithoutCorrections() = runBlocking {
        val suggestionRepo = FakeSuggestionHistoryRepository()
        val correctionRepo = FakeCorrectionHistoryRepository()
        val recorder = RecordAiLearningEventUseCase(suggestionRepo, correctionRepo)
        val context = context()

        recorder.recordAccepted(
            context = context,
            decision = CaptureSuggestionLearningDecision(
                surface = AiSuggestionSurface.Capture,
                userAction = "create_task",
                finalType = SuggestedItemType.Task,
                finalSpaceId = 1,
                finalSpaceName = "Work",
                finalTitle = "Ask manager about budget",
                sourceText = "Ask manager about budget",
            ),
        )

        assertEquals(AiSuggestionOutcome.Accepted, suggestionRepo.items.single().outcome)
        assertEquals(emptyList<AiCorrectionHistoryEntity>(), correctionRepo.items)
    }

    @Test
    fun correctedSuggestionStoresFieldCorrections() = runBlocking {
        val suggestionRepo = FakeSuggestionHistoryRepository()
        val correctionRepo = FakeCorrectionHistoryRepository()
        val recorder = RecordAiLearningEventUseCase(suggestionRepo, correctionRepo)
        val context = context()
        val decision = CaptureSuggestionLearningDecision(
            surface = AiSuggestionSurface.Capture,
            userAction = "save_note",
            finalType = SuggestedItemType.Note,
            finalSpaceId = null,
            finalSpaceName = "Inbox",
            finalTitle = "Budget worry",
            sourceText = "Ask manager about budget",
        )

        assertTrue(recorder.hasCorrections(context, decision))
        recorder.recordCorrected(context, decision)

        assertEquals(AiSuggestionOutcome.Corrected, suggestionRepo.items.single().outcome)
        assertEquals(listOf("type", "space", "title"), correctionRepo.items.map { it.fieldName })
        assertEquals(1L, correctionRepo.items.single { it.fieldName == "type" }.suggestionHistoryId)
    }

    @Test
    fun rejectedBrainDumpSuggestionKeepsBrainDumpSurface() = runBlocking {
        val suggestionRepo = FakeSuggestionHistoryRepository()
        val recorder = RecordAiLearningEventUseCase(
            suggestionHistoryRepository = suggestionRepo,
            correctionHistoryRepository = FakeCorrectionHistoryRepository(),
        )

        recorder.recordRejected(
            context = context(),
            userAction = "keep_brain_dump_item_in_inbox",
            surface = AiSuggestionSurface.BrainDump,
            sourceItemId = "dump_1",
            sourceText = "car noise tomorrow",
        )

        assertEquals(AiSuggestionSurface.BrainDump, suggestionRepo.items.single().surface)
        assertEquals(AiSuggestionOutcome.Rejected, suggestionRepo.items.single().outcome)
    }

    private fun context() = CaptureSuggestionLearningContext(
        captureId = 42,
        suggestedSpaceId = 1,
        analysis = CaptureAnalysis(
            rawText = "Ask manager about budget",
            suggestedType = SuggestedItemType.Task,
            suggestedSpaceName = "Work",
            suggestedTitle = "Ask manager about budget",
            summary = "Ask manager about budget",
            possibleMondayItem = false,
            suggestedNextAction = "Ask manager about budget",
            relatedTopics = listOf("manager"),
            reminderPossible = false,
            confidence = 0.82f,
        ),
    )

    private class FakeSuggestionHistoryRepository : AiSuggestionHistoryRepository {
        val items = mutableListOf<AiSuggestionHistoryEntity>()
        private val flow = MutableStateFlow<List<AiSuggestionHistoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<AiSuggestionHistoryEntity>> = flow
        override suspend fun getById(id: Long): AiSuggestionHistoryEntity? =
            items.firstOrNull { it.id == id }

        override suspend fun insert(entity: AiSuggestionHistoryEntity): Long {
            val id = (items.maxOfOrNull { it.id } ?: 0L) + 1
            items += entity.copy(id = id)
            flow.value = items
            return id
        }

        override suspend fun update(entity: AiSuggestionHistoryEntity) {
            items.replaceAll { if (it.id == entity.id) entity else it }
            flow.value = items
        }

        override suspend fun delete(entity: AiSuggestionHistoryEntity) = deleteById(entity.id)

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
            flow.value = items
        }

        override suspend fun deleteAll() {
            items.clear()
            flow.value = emptyList()
        }
    }

    private class FakeCorrectionHistoryRepository : AiCorrectionHistoryRepository {
        val items = mutableListOf<AiCorrectionHistoryEntity>()
        private val flow = MutableStateFlow<List<AiCorrectionHistoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<AiCorrectionHistoryEntity>> = flow
        override suspend fun getById(id: Long): AiCorrectionHistoryEntity? =
            items.firstOrNull { it.id == id }

        override suspend fun insert(entity: AiCorrectionHistoryEntity): Long {
            val id = (items.maxOfOrNull { it.id } ?: 0L) + 1
            items += entity.copy(id = id)
            flow.value = items
            return id
        }

        override suspend fun update(entity: AiCorrectionHistoryEntity) {
            items.replaceAll { if (it.id == entity.id) entity else it }
            flow.value = items
        }

        override suspend fun delete(entity: AiCorrectionHistoryEntity) = deleteById(entity.id)

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
            flow.value = items
        }

        override suspend fun deleteAll() {
            items.clear()
            flow.value = emptyList()
        }
    }
}
