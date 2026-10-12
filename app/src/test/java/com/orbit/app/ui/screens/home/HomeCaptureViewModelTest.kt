package com.orbit.app.ui.screens.home

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.OrbitContainer
import com.orbit.app.R
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.capture.AutoFiler
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The save receipt is state, not a flash: it follows the stored thought, says only
 * what is true now, and comes back after the app is stopped.
 */
@RunWith(AndroidJUnit4::class)
class HomeCaptureViewModelTest {
    private val now = System.currentTimeMillis()
    private val scopes = mutableListOf<CoroutineScope>()
    private val stores = mutableListOf<ViewModelStore>()
    private val databases = mutableListOf<OrbitDatabase>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        scopes.forEach { it.cancel() }
        databases.forEach { it.close() }
    }

    @Test
    fun theReceiptSaysSavedAtOnceThenFiledWithTheSpaceOnceTheThoughtIsFiled() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        runBlocking { app.spaceRepository.insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0)) }
        val viewModel = app.viewModel()

        viewModel.onInputChanged(CabinNote)
        assertTrue(viewModel.send())

        awaitUntil { viewModel.receipt.value != null }
        val saved = requireNotNull(viewModel.receipt.value)
        assertEquals(CaptureOutcome.Saved, saved.outcome)
        assertEquals(R.string.receipt_saved, saved.line().textRes)
        assertEquals(saved.captureId, app.settings.value.lastReceiptCaptureId)

        // Analysis lands, then the thought is filed the way automatic filing files it.
        awaitUntil { runBlocking { app.database.captureSuggestionDao().getByCaptureId(saved.captureId) } != null }
        assertTrue(runBlocking { app.autoFiler.apply(saved.captureId) })

        awaitUntil { viewModel.receipt.value?.outcome == CaptureOutcome.Filed }
        val filed = requireNotNull(viewModel.receipt.value)
        assertEquals(saved.captureId, filed.captureId)
        assertEquals("Home", filed.spaceName)
        assertEquals(ReceiptItemType.Note, filed.itemType)
        assertEquals(ReceiptLine(R.string.receipt_filed_note, space = "Home"), filed.line())
        assertEquals(ReceiptAction.Change, filed.action())
    }

    @Test
    fun aReminderWithoutAnAlarmSaysItIsNotSetYetAndTryAgainArmsIt() {
        val scheduler = TokenScheduler(armed = false)
        val app = TestApp(scheduler = scheduler)
        val captureId = runBlocking { app.inbox.save("Call the bank") }
        val reminderAt = now + 60L * 60_000L
        // The outcome row says ReminderSet, but scheduling gave no alarm.
        val reminderId = runBlocking { app.confirm.createReminder(captureId, null, "Call the bank", reminderAt) }
        assertNull(runBlocking { app.database.reminderDao().getById(reminderId) }?.notificationWorkId)
        assertEquals(CaptureOutcome.ReminderSet, runBlocking { app.database.captureDao().getById(captureId) }?.outcome)
        app.settings.value = AppSettings(lastReceiptCaptureId = captureId)

        val viewModel = app.viewModel()
        awaitUntil { viewModel.receipt.value != null }
        val notArmed = requireNotNull(viewModel.receipt.value)
        assertEquals(R.string.receipt_reminder_not_armed, notArmed.line().textRes)
        assertNotEquals(R.string.receipt_reminder_set, notArmed.line().textRes)
        assertEquals(ReceiptAction.TryAgain, notArmed.action())

        scheduler.armed = true
        viewModel.retryReceiptReminder()
        awaitUntil { viewModel.receipt.value?.reminderArmed == true }
        val armed = requireNotNull(viewModel.receipt.value)
        assertEquals(ReceiptLine(R.string.receipt_reminder_set, time = ReceiptTime.Moment(reminderAt)), armed.line())
        assertEquals(ReceiptAction.Change, armed.action())
    }

    @Test
    fun aReminderWithoutATimeAsksOneQuestionInPlaceOfTheReceiptAndJustKeepItEndsIt() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        val viewModel = app.viewModel()
        viewModel.onInputChanged("remind me about the car")
        assertTrue(viewModel.send())
        awaitUntil { viewModel.receipt.value != null }
        val captureId = requireNotNull(viewModel.receipt.value).captureId
        awaitUntil { runBlocking { app.database.captureSuggestionDao().getByCaptureId(captureId) } != null }
        assertTrue(runBlocking { app.autoFiler.apply(captureId) })

        // The question is read from the stored outcome, not from a one-off event.
        awaitUntil { viewModel.question.value != null }
        val question = requireNotNull(viewModel.question.value)
        assertEquals(captureId, question.captureId)
        assertEquals(CaptureOutcomeReason.TimeUnclear, question.reason)
        assertNull(runBlocking { app.database.reminderDao().observeAll().first() }.firstOrNull())

        viewModel.keepQuestionThought()
        awaitUntil { viewModel.question.value == null && viewModel.receipt.value?.outcome == CaptureOutcome.Saved }
        val kept = requireNotNull(runBlocking { app.database.captureDao().getById(captureId) })
        assertEquals(CaptureOutcome.Saved, kept.outcome)
        assertEquals(CaptureOutcomeReason.None, kept.outcomeReason)
        assertNotNull(kept.decidedAt)
        assertEquals(R.string.receipt_kept, requireNotNull(viewModel.receipt.value).line().textRes)
        assertTrue(runBlocking { app.database.reminderDao().observeAll().first() }.isEmpty())
    }

    @Test
    fun aCalendarThoughtStillGetsItsSheetAfterAndroidStoppedTheApp() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        val day = java.time.LocalDate.of(2026, 7, 20).toEpochDay()
        val savedState = SavedStateHandle()
        val before = app.viewModel(savedState)
        before.onInputChanged("Dentist")
        assertTrue(before.send(calendarDateContextEpochDay = day))
        awaitUntil { before.receipt.value != null }
        val captureId = requireNotNull(before.receipt.value).captureId
        // The ViewModel is gone (process death) before the sheet could open...
        stores.forEach { it.clear() }
        awaitUntil { runBlocking { app.database.captureSuggestionDao().getByCaptureId(captureId) } != null }

        // ...and the new one, restored from the saved state, still opens it.
        val after = app.viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        awaitUntil { after.uiState.value.sortRequest != null }
        assertEquals(captureId, after.uiState.value.sortRequest?.captureId)
        after.sortRequestHandled()
        // Opened once only.
        val third = app.viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(third.uiState.value.sortRequest)
    }

    @Test
    fun eachSendIsShownOnce_evenWhenHomeComesBackFromAnotherTab() {
        val viewModel = TestApp(scheduler = TokenScheduler(armed = true)).viewModel()
        assertFalse(viewModel.claimSavedPulse(0))
        assertTrue(viewModel.claimSavedPulse(1))
        // Home re-entered composition with the same pulse: nothing is replayed.
        assertFalse(viewModel.claimSavedPulse(1))
        assertTrue(viewModel.claimSavedPulse(2))
    }

    @Test
    fun textSharedWhileAThoughtIsSavingWaitsForTheBoxInsteadOfBeingDropped() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        val viewModel = app.viewModel()
        viewModel.onInputChanged("First thought")
        assertTrue(viewModel.send())
        viewModel.receiveSharedText("Shared meanwhile")

        awaitUntil { viewModel.receipt.value != null && !viewModel.uiState.value.isProcessing }
        assertEquals("Shared meanwhile", viewModel.uiState.value.inputText)
    }

    @Test
    fun aCalendarThoughtStillOpensItsSheetWhenAnotherIsSavedBeforeItsAnalysisLands() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        val slow = CompletableDeferred<Unit>().also { app.heldBack["Dentist"] = it }
        val viewModel = app.viewModel()
        val day = java.time.LocalDate.of(2026, 7, 20).toEpochDay()

        viewModel.onInputChanged("Dentist")
        assertTrue(viewModel.send(calendarDateContextEpochDay = day))
        awaitUntil { viewModel.receipt.value != null }
        val calendarThought = requireNotNull(viewModel.receipt.value).captureId

        // A second thought is saved and analysed while the first is still being analysed.
        viewModel.onInputChanged("parking code 4826")
        assertTrue(viewModel.send())
        awaitUntil { viewModel.receipt.value?.captureId?.let { it != calendarThought } == true }
        val plain = requireNotNull(viewModel.receipt.value).captureId
        awaitUntil { runBlocking { app.database.captureSuggestionDao().getByCaptureId(plain) } != null }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(viewModel.uiState.value.sortRequest)

        slow.complete(Unit)
        awaitUntil { viewModel.uiState.value.sortRequest != null }
        assertEquals(calendarThought, viewModel.uiState.value.sortRequest?.captureId)
    }

    @Test
    fun theNextSaveReplacesTheReceiptAndDismissingHidesItUntilThen() {
        val app = TestApp(scheduler = TokenScheduler(armed = true))
        val viewModel = app.viewModel()
        viewModel.onInputChanged("parking code 4826")
        viewModel.send()
        awaitUntil { viewModel.receipt.value != null }
        val first = requireNotNull(viewModel.receipt.value).captureId

        viewModel.dismissReceipt()
        awaitUntil { viewModel.receipt.value == null }
        assertTrue(app.settings.value.lastReceiptDismissed)

        viewModel.onInputChanged("Ideas for the garden")
        viewModel.send()
        awaitUntil { viewModel.receipt.value != null }
        assertNotEquals(first, viewModel.receipt.value?.captureId)
        assertEquals(false, app.settings.value.lastReceiptDismissed)
    }

    @Test
    fun theReceiptComesBackAfterTheWholeContainerIsRecreated() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val first = OrbitContainer(application)
        val before = containerViewModel(first)
        before.onInputChanged("Buy milk tomorrow")
        assertTrue(before.send())
        awaitUntil { before.receipt.value != null }
        val captureId = requireNotNull(before.receipt.value).captureId
        // The container files the clear thought itself as soon as its analysis lands.
        awaitUntil { before.receipt.value?.outcome == CaptureOutcome.Filed }
        val shown = requireNotNull(before.receipt.value)
        assertEquals(ReceiptItemType.Task, shown.itemType)
        stores.forEach { it.clear() }

        // As after a force-stop: new objects over the same settings store and database.
        val second = OrbitContainer(application)
        val after = containerViewModel(second)
        awaitUntil { after.receipt.value != null }
        val restored = requireNotNull(after.receipt.value)
        assertEquals(shown, restored)
        assertEquals(shown.line(), restored.line())
        assertEquals(shown.action(), restored.action())
    }

    private fun containerViewModel(container: OrbitContainer): HomeCaptureViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = HomeCaptureViewModel.Factory(
            captureInbox = container.captureInbox,
            appSettingsRepository = container.appSettingsRepository,
            savedStateHandle = SavedStateHandle(),
            receipts = CaptureReceiptReader(
                captureRepository = container.captureRepository,
                noteRepository = container.noteRepository,
                taskRepository = container.taskRepository,
                reminderRepository = container.reminderRepository,
                spaceRepository = container.spaceRepository,
            )::observe,
        )
        return ViewModelProvider.create(store, factory)[HomeCaptureViewModel::class.java]
    }

    /** Waits for [condition], letting the main thread (where the view model works) run meanwhile. */
    private fun awaitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting")
            Thread.sleep(10)
        }
    }

    private class TokenScheduler(var armed: Boolean) : ReminderScheduler {
        private var counter = 0
        override fun schedule(reminder: ReminderEntity): String? = if (armed) "token-${++counter}" else null
        override fun cancel(reminderId: Long) = Unit
    }

    private class MemorySettings(val state: MutableStateFlow<AppSettings>) : AppSettingsRepository {
        override val settings = state
        override suspend fun update(settings: AppSettings) {
            state.value = settings
        }
        override suspend fun reset() {
            state.value = AppSettings()
        }
    }

    private inner class TestApp(scheduler: ReminderScheduler) {
        val database = inMemoryOrbitDatabase().also { databases += it }
        val settings = MutableStateFlow(AppSettings())

        /** Analysis of these texts waits until the test completes the gate. */
        val heldBack = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private val transaction = RoomCaptureFinalizationTransaction(database)
        private val captures = RoomCaptureRepository(database.captureDao())
        private val notes = RoomNoteRepository(database.noteDao())
        private val tasks = RoomTaskRepository(database.taskDao())
        private val reminders = RoomReminderRepository(database.reminderDao(), scheduler)
        val spaceRepository = RoomSpaceRepository(database.spaceDao())
        private val outcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
            NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
        }

        val inbox = CaptureInbox(
            captureRepository = captures,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            spaceRepository = spaceRepository,
            suggester = { text, _ ->
                heldBack[text]?.await()
                if (text == CabinNote) {
                    CaptureAnalysis(
                        rawText = text,
                        suggestedType = SuggestedItemType.Note,
                        suggestedSpaceName = "Home",
                        suggestedTitle = "Cabin wifi password",
                        suggestedNextAction = "",
                        relatedTopics = listOf("Home"),
                        reminderPossible = false,
                        confidence = 0.9f,
                    )
                } else {
                    LocalRulesCaptureAnalyzer(
                        now = { Instant.ofEpochMilli(now) },
                        zoneId = { ZoneId.systemDefault() },
                    ).analyze(text)
                }
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it },
            transaction = transaction,
        )

        val confirm = ConfirmCaptureActionUseCase(
            captureRepository = captures,
            noteRepository = notes,
            taskRepository = tasks,
            reminderRepository = reminders,
            transaction = transaction,
            labelRepository = RoomLabelRepository(database.labelDao()),
        )

        private val resolution = CaptureResolution(
            captureRepository = captures,
            noteRepository = notes,
            taskRepository = tasks,
            reminderRepository = reminders,
            spaceRepository = spaceRepository,
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = confirm,
            transaction = transaction,
            reminderOutcomes = outcomes,
        )

        val autoFiler = AutoFiler(
            captureRepository = captures,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            captureResolution = resolution,
            transaction = transaction,
        )

        fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): HomeCaptureViewModel {
            val store = ViewModelStore().also { stores += it }
            val factory = HomeCaptureViewModel.Factory(
                captureInbox = inbox,
                appSettingsRepository = MemorySettings(settings),
                savedStateHandle = savedState,
                receipts = CaptureReceiptReader(captures, notes, tasks, reminders, spaceRepository)::observe,
                retryReminder = { captureId, reminderId ->
                    rearmReceiptReminder(reminders, outcomes, resolution, captureId, reminderId)
                },
                keepThought = resolution::keep,
            )
            return ViewModelProvider.create(store, factory)[HomeCaptureViewModel::class.java]
        }
    }

    private companion object {
        const val CabinNote = "Wifi password for the cabin is on the fridge"
    }
}
