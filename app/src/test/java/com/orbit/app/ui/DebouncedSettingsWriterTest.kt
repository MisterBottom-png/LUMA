package com.orbit.app.ui

import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.domain.model.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedSettingsWriterTest {
    private class RecordingRepository(var failNext: Boolean = false) : AppSettingsRepository {
        val stored = MutableStateFlow(AppSettings())
        val writes = mutableListOf<AppSettings>()
        override val settings: Flow<AppSettings> = stored
        override suspend fun update(settings: AppSettings) {
            if (failNext) {
                failNext = false
                error("disk full")
            }
            writes += settings
            stored.value = settings
        }
        override suspend fun reset() {
            stored.value = AppSettings()
        }
    }

    @Test
    fun rapidChangesShowImmediately_andAreWrittenOnceInOrder() = runTest {
        val repository = RecordingRepository()
        val writer = DebouncedSettingsWriter(backgroundScope, repository, debounceMillis = 250)
        runCurrent()

        (1..20).forEach { step ->
            writer.submit(AppSettings(backgroundDim = step / 100f))
            advanceTimeBy(20)
        }
        assertEquals(0.20f, writer.overlay.value?.backgroundDim)
        assertEquals(0, repository.writes.size)

        settle()

        assertEquals(listOf(0.20f), repository.writes.map { it.backgroundDim })
        assertNull(writer.overlay.value)
    }

    @Test
    fun failedWriteFallsBackToWhatIsReallyStored() = runTest {
        val repository = RecordingRepository(failNext = true)
        val writer = DebouncedSettingsWriter(backgroundScope, repository, debounceMillis = 250)
        runCurrent()

        writer.submit(AppSettings(staleLoopDays = 30))
        settle()

        assertNull(writer.overlay.value)
        assertEquals(AppSettings().staleLoopDays, repository.stored.value.staleLoopDays)
    }

    @Test
    fun aChangeMadeDuringAWriteIsNotDropped() = runTest {
        val repository = RecordingRepository()
        val writer = DebouncedSettingsWriter(backgroundScope, repository, debounceMillis = 250)
        runCurrent()

        writer.submit(AppSettings(staleLoopDays = 3))
        settle()
        writer.submit(AppSettings(staleLoopDays = 5))
        settle()

        assertEquals(listOf(3, 5), repository.writes.map { it.staleLoopDays })
        assertNull(writer.overlay.value)
    }

    /** Background work is not covered by advanceUntilIdle, so move the clock past the debounce. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }
}
