package com.orbit.app.ui.screens.home

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A new user learns the app from Home: the first save ends first run, the tip under the
 * first receipt says what really happened, and notifications are asked for only when a
 * reminder saved just now needs them.
 */
@RunWith(AndroidJUnit4::class)
class HomeFirstRunTest {
    private val database: OrbitDatabase = inMemoryOrbitDatabase()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val settings = MutableStateFlow(AppSettings())

    /** What the stored thought currently is; the receipt of any saved id follows it. */
    private val stored = MutableStateFlow<CaptureReceipt?>(null)
    private val retried = mutableListOf<Pair<Long, Long>>()

    private val inbox = CaptureInbox(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        suggestionDao = database.captureSuggestionDao(),
        brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggester = { text, _ -> LocalRulesCaptureAnalyzer().analyze(text) },
        scope = scope,
        transaction = RoomCaptureFinalizationTransaction(database),
    )

    private val viewModel: HomeCaptureViewModel by lazy {
        val factory = HomeCaptureViewModel.Factory(
            captureInbox = inbox,
            appSettingsRepository = MemorySettings(settings),
            savedStateHandle = SavedStateHandle(),
            receipts = { id -> stored.map { it?.copy(captureId = id) } },
            retryReminder = { captureId, reminderId -> retried += captureId to reminderId },
        )
        ViewModelProvider.create(store, factory)[HomeCaptureViewModel::class.java]
    }

    @After
    fun tearDown() {
        store.clear()
        scope.cancel()
        database.close()
    }

    @Test
    fun theFirstSaveEndsFirstRunAndTheTipFollowsWhatHappened() {
        stored.value = receipt(CaptureOutcome.Saved)
        save("parking code 4826")
        awaitUntil { settings.value.hasCompletedFirstTimeTutorial }
        awaitUntil { viewModel.firstReceiptTip.value == FirstReceiptTip.Kept }

        stored.value = receipt(CaptureOutcome.Filed, itemType = ReceiptItemType.Note)
        awaitUntil { viewModel.firstReceiptTip.value == FirstReceiptTip.Filed }

        stored.value = receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = true)
        awaitUntil { viewModel.firstReceiptTip.value == FirstReceiptTip.Reminder }

        // A question is its own tip, and a reminder without its alarm is not called set.
        stored.value = receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimeUnclear)
        awaitUntil { viewModel.receipt.value?.outcome == CaptureOutcome.NeedsYou }
        assertNull(viewModel.firstReceiptTip.value)
        stored.value = receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = false)
        awaitUntil { viewModel.receipt.value?.outcome == CaptureOutcome.ReminderSet }
        assertNull(viewModel.firstReceiptTip.value)
    }

    @Test
    fun theTipIsShownOnceAndIsReadWhenTheNextThoughtIsSent() {
        stored.value = receipt(CaptureOutcome.Saved)
        save("Ideas for the garden")
        awaitUntil { viewModel.firstReceiptTip.value == FirstReceiptTip.Kept }
        assertFalse(settings.value.hasSeenFirstReceiptTip)

        save("Renew passport")
        awaitUntil { settings.value.hasSeenFirstReceiptTip }
        awaitUntil { viewModel.receipt.value != null }
        assertNull(viewModel.firstReceiptTip.value)
    }

    @Test
    fun closingTheFirstReceiptAlsoEndsTheTip() {
        stored.value = receipt(CaptureOutcome.Filed, itemType = ReceiptItemType.Task)
        save("Buy milk tomorrow")
        awaitUntil { viewModel.firstReceiptTip.value == FirstReceiptTip.Filed }

        viewModel.dismissReceipt()
        awaitUntil { settings.value.hasSeenFirstReceiptTip && settings.value.lastReceiptDismissed }
        assertNull(viewModel.firstReceiptTip.value)
    }

    @Test
    fun someoneWhoAlreadyReadTheTipNeverSeesItAgain() {
        settings.value = AppSettings(hasCompletedFirstTimeTutorial = true, hasSeenFirstReceiptTip = true)
        stored.value = receipt(CaptureOutcome.Saved)
        save("parking code 4826")
        awaitUntil { viewModel.receipt.value != null }
        assertNull(viewModel.firstReceiptTip.value)
    }

    @Test
    fun notificationsAreNeverAskedForAReceiptThatWasOnlyRestored() {
        // As on a cold start: the stored receipt is a reminder that cannot ring.
        settings.value = AppSettings(lastReceiptCaptureId = 5L)
        stored.value = receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff, ReceiptItemType.Reminder)
        awaitUntil { viewModel.receipt.value != null }
        assertNull(viewModel.notificationPriming.value)
    }

    @Test
    fun aReminderSavedNowThatCannotRingAsksOnceAndIsArmedWhenAllowed() {
        stored.value = receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff, ReceiptItemType.Reminder)
        save("Remind me tomorrow at 9:00 to call the bank")
        awaitUntil { viewModel.notificationPriming.value != null }
        val primed = requireNotNull(viewModel.notificationPriming.value)

        viewModel.notificationPermissionAnswered(granted = true)
        awaitUntil { settings.value.hasAskedForNotifications }
        awaitUntil { retried.isNotEmpty() }
        assertEquals(listOf(primed.captureId to ReminderId), retried)
        awaitUntil { viewModel.notificationPriming.value == null }
    }

    @Test
    fun aDeniedAnswerIsAlsoAskedOnlyOnce() {
        stored.value = receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff, ReceiptItemType.Reminder)
        save("Remind me tomorrow at 9:00 to water the roses")
        awaitUntil { viewModel.notificationPriming.value != null }

        viewModel.notificationPermissionAnswered(granted = false)
        awaitUntil { settings.value.hasAskedForNotifications }
        awaitUntil { viewModel.notificationPriming.value == null }
        assertTrue(retried.isEmpty())

        save("Remind me tomorrow at 10:00 to call the garage")
        awaitUntil { viewModel.receipt.value != null }
        assertNull(viewModel.notificationPriming.value)
    }

    @Test
    fun aPlainThoughtNeverAsksForNotifications() {
        stored.value = receipt(CaptureOutcome.Saved)
        save("parking code 4826")
        awaitUntil { viewModel.receipt.value != null }
        assertNull(viewModel.notificationPriming.value)
    }

    @Test
    fun tipAndNotificationRulesPerOutcome() {
        assertEquals(FirstReceiptTip.Kept, firstReceiptTipFor(receipt(CaptureOutcome.Saved)))
        assertEquals(FirstReceiptTip.Filed, firstReceiptTipFor(receipt(CaptureOutcome.Filed, itemType = ReceiptItemType.Note)))
        assertEquals(
            FirstReceiptTip.Reminder,
            firstReceiptTipFor(receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = true)),
        )
        assertNull(firstReceiptTipFor(receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder)))
        CaptureOutcomeReason.entries.forEach { reason ->
            assertNull(reason.name, firstReceiptTipFor(receipt(CaptureOutcome.NeedsYou, reason)))
        }

        assertTrue(receipt(CaptureOutcome.ReminderSet, itemType = ReceiptItemType.Reminder, reminderArmed = true).wantsNotifications())
        assertTrue(receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff, ReceiptItemType.Reminder).wantsNotifications())
        assertFalse(receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimeUnclear).wantsNotifications())
        assertFalse(receipt(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled, ReceiptItemType.Reminder).wantsNotifications())
        assertFalse(receipt(CaptureOutcome.Filed, itemType = ReceiptItemType.Task).wantsNotifications())
        assertFalse(receipt(CaptureOutcome.Saved).wantsNotifications())
    }

    @Test
    fun afterFirstSaveKeepsAnEarlierReadTip() {
        val read = AppSettings(hasSeenFirstReceiptTip = true).afterFirstSave(tipWasShown = false)
        assertTrue(read.hasCompletedFirstTimeTutorial)
        assertTrue(read.hasSeenFirstReceiptTip)
        val unread = AppSettings().afterFirstSave(tipWasShown = false)
        assertTrue(unread.hasCompletedFirstTimeTutorial)
        assertFalse(unread.hasSeenFirstReceiptTip)
    }

    private fun save(text: String) {
        viewModel.onInputChanged(text)
        assertTrue(viewModel.send())
        awaitUntil { !viewModel.uiState.value.isProcessing }
    }

    private fun receipt(
        outcome: CaptureOutcome,
        reason: CaptureOutcomeReason = CaptureOutcomeReason.None,
        itemType: ReceiptItemType? = null,
        reminderArmed: Boolean = false,
    ) = CaptureReceipt(
        captureId = 5L,
        outcome = outcome,
        reason = reason,
        decided = true,
        title = "A thought",
        itemType = itemType,
        itemId = itemType?.let { ReminderId },
        reminderAt = (System.currentTimeMillis() + 86_400_000L).takeIf { itemType == ReceiptItemType.Reminder },
        reminderArmed = reminderArmed,
    )

    /** Waits for [condition], letting the main thread (where the view model works) run meanwhile. */
    private fun awaitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting")
            Thread.sleep(10)
        }
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

    private companion object {
        const val ReminderId = 11L
    }
}
