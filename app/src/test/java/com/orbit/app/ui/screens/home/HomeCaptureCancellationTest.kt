package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.repository.CaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCaptureCancellationTest {
    @Test
    fun cancellingInboxCaptureArchivesIt() = runBlocking {
        val repository = FakeCaptureRepository(
            CaptureEntity(id = 7L, rawText = "Cancel this", status = CaptureStatus.Inbox),
        )

        archiveCancelledCapture(repository, captureId = 7L, now = 123L)

        assertEquals(CaptureStatus.Archived, repository.item?.status)
        assertEquals(123L, repository.item?.updatedAt)
    }

    @Test
    fun cancellingDoesNotChangeFinalizedCapture() = runBlocking {
        val original = CaptureEntity(
            id = 8L,
            rawText = "Already finalized",
            status = CaptureStatus.Processed,
            updatedAt = 42L,
        )
        val repository = FakeCaptureRepository(original)

        archiveCancelledCapture(repository, captureId = 8L, now = 123L)

        assertEquals(original, repository.item)
    }
}

private class FakeCaptureRepository(
    var item: CaptureEntity?,
) : CaptureRepository {
    override fun observeAll(): Flow<List<CaptureEntity>> = flowOf(listOfNotNull(item))

    override suspend fun getById(id: Long): CaptureEntity? = item?.takeIf { it.id == id }

    override suspend fun insert(entity: CaptureEntity): Long = error("Not used")

    override suspend fun update(entity: CaptureEntity) {
        item = entity
    }

    override suspend fun delete(entity: CaptureEntity) = error("Not used")

    override suspend fun deleteById(id: Long) = error("Not used")
}
