package com.orbit.app.ui.screens.review

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.domain.capture.quickReminderTimes
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Needs you holds only thoughts with one real question; plain saved thoughts never appear there. */
@RunWith(AndroidJUnit4::class)
class ReviewNeedsYouTest {
    private lateinit var container: OrbitContainer
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: ReviewViewModel

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container = OrbitContainer(application)
        // The app's database is one per process: start every case from empty tables.
        runBlocking { withContext(Dispatchers.IO) { container.database.clearAllTables() } }
        store = ViewModelStore()
        viewModel = ViewModelProvider.create(store, ReviewViewModel.Factory(container))[ReviewViewModel::class.java]
    }

    @After
    fun tearDown() {
        store.clear()
        container.applicationScope.cancel()
    }

    /**
     * The ViewModel works on the main looper, which this test thread owns: keep one
     * collector on the state and run the looper until [predicate] holds.
     */
    private fun state(predicate: (ReviewUiState) -> Boolean): ReviewUiState {
        if (collector == null) collector = viewModel.viewModelScope.launch { viewModel.uiState.collect {} }
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            val current = viewModel.uiState.value
            if (predicate(current)) return current
            check(System.currentTimeMillis() < deadline) { "Needs you never reached the expected state: $current" }
            Thread.sleep(20)
        }
    }

    private var collector: Job? = null

    @Test
    fun threeSavedAndOneQuestionShowOneCard_andJustKeepItEmptiesIt() = runBlocking {
        repeat(3) { container.captureRepository.insert(CaptureEntity(rawText = "Saved thought $it", decidedAt = 1L)) }
        val askId = container.captureRepository.insert(
            CaptureEntity(
                rawText = "remind me about the car",
                outcome = CaptureOutcome.NeedsYou,
                outcomeReason = CaptureOutcomeReason.TimeUnclear,
                decidedAt = 1L,
            ),
        )

        val asking = state { it.needsYou.isNotEmpty() }
        assertEquals(1, asking.needsYou.size)
        assertEquals(askId, asking.needsYou.single().captureId)
        assertFalse(asking.nothingWaiting)
        assertFalse("a time question is not a failure", asking.somethingFailed)

        viewModel.keepThought(asking.needsYou.single())

        val answered = state { it.needsYou.isEmpty() }
        assertTrue(answered.nothingWaiting)
        val kept = requireNotNull(container.captureRepository.getById(askId))
        assertEquals(CaptureOutcome.Saved, kept.outcome)
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertNotNull(kept.decidedAt)
    }

    @Test
    fun aQuickTimeSetsTheReminderAndRemovesTheCard() = runBlocking {
        val askId = container.captureRepository.insert(
            CaptureEntity(
                rawText = "remind me to water the plants",
                outcome = CaptureOutcome.NeedsYou,
                outcomeReason = CaptureOutcomeReason.TimeUnclear,
                decidedAt = 1L,
            ),
        )
        val item = state { it.needsYou.isNotEmpty() }.needsYou.single()
        val tomorrowMorning = quickReminderTimes(System.currentTimeMillis(), ZoneId.systemDefault()).last()

        viewModel.remindAt(item, tomorrowMorning)

        state { it.needsYou.isEmpty() }
        val capture = requireNotNull(container.captureRepository.getById(askId))
        assertEquals(CaptureOutcome.ReminderSet, capture.outcome)
        val reminder = container.reminderRepository.observeAll().first().single { it.linkedCaptureId == askId }
        assertEquals(tomorrowMorning, reminder.dueAt)
    }

    @Test
    fun aTaskDueTodayIsARowNotACard() = runBlocking {
        val now = System.currentTimeMillis()
        container.taskRepository.insert(TaskEntity(title = "Pay the bill", scheduledDateEpochDay = LocalDate.now().toEpochDay()))
        container.captureRepository.insert(CaptureEntity(rawText = "Plain idea", decidedAt = now))

        val loaded = state { it.dueToday.isNotEmpty() }
        assertEquals(listOf("Pay the bill"), loaded.dueToday.map { it.title })
        assertTrue(loaded.needsYou.isEmpty())
        assertFalse(loaded.nothingWaiting)
        assertEquals(1, loaded.keptToday)
        assertEquals(0, loaded.filedToday)
    }

    @Test
    fun buildNeedsYou_readsOnlyNeedsYou_andFindsTheLinkedReminder() {
        val captures = listOf(
            CaptureEntity(id = 1, rawText = "kept", outcome = CaptureOutcome.Saved),
            CaptureEntity(id = 2, rawText = "filed", outcome = CaptureOutcome.Filed, status = CaptureStatus.Processed, linkedItemId = 9),
            CaptureEntity(
                id = 3, rawText = "can't ring", outcome = CaptureOutcome.NeedsYou, outcomeReason = CaptureOutcomeReason.NotificationsOff,
                status = CaptureStatus.Processed, linkedItemId = 7, updatedAt = 30,
            ),
            CaptureEntity(
                id = 4, rawText = "archived question", outcome = CaptureOutcome.NeedsYou,
                outcomeReason = CaptureOutcomeReason.TimeUnclear, status = CaptureStatus.Archived,
            ),
            CaptureEntity(
                id = 5, rawText = "when?", outcome = CaptureOutcome.NeedsYou, outcomeReason = CaptureOutcomeReason.TimeUnclear,
                updatedAt = 10,
            ),
        )
        val reminders = listOf(ReminderEntity(id = 7, title = "Call the bank", dueAt = 1_000L, linkedCaptureId = 3))

        val items = buildNeedsYou(captures, emptyMap(), reminders)

        assertEquals(listOf(3L, 5L), items.map { it.captureId })
        assertEquals(7L, items[0].reminderId)
        assertEquals("Call the bank", items[0].title)
        assertNull(items[1].reminderId)
        assertTrue(needsYouSomethingFailed(captures))
        assertFalse(needsYouSomethingFailed(captures.filter { it.id != 3L }))
    }

    @Test
    fun quickTimesOfferThisEveningOnlyWhileItIsAhead() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 10, 12)
        val morning = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val lateAfternoon = day.atTime(17, 50).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            listOf(day.atTime(18, 0), day.plusDays(1).atTime(9, 0)).map { it.atZone(zone).toInstant().toEpochMilli() },
            quickReminderTimes(morning, zone),
        )
        assertEquals(
            listOf(day.plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()),
            quickReminderTimes(lateAfternoon, zone),
        )
    }

}
