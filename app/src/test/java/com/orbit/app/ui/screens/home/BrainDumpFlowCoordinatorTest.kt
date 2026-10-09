package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.usecase.BrainDumpActionResult
import com.orbit.app.domain.usecase.BrainDumpActionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BrainDumpFlowCoordinatorTest {
    @Test
    fun discardRemainingCancelsPendingSkipWithoutCommitAndClosesAfterTransactionalBoundary() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = coordinatorFixture(
            itemCount = 2,
            onClose = { resumable -> events += "close:$resumable" },
            discardRemaining = { captureId -> events += "discard:$captureId" },
        ) { request ->
            events += request::class.simpleName.orEmpty()
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()
        fixture.coordinator.discardRemaining()

        assertEquals(listOf("discard:7", "close:false"), events)
        assertNull(fixture.coordinator.state.value)
    }

    @Test
    fun failedDiscardRestoresPendingSessionAndRetryPreservesDiscardIntent() = runBlocking {
        var shouldFail = true
        val events = mutableListOf<String>()
        val fixture = coordinatorFixture(
            itemCount = 2,
            onClose = { resumable -> events += "close:$resumable" },
            discardRemaining = { captureId ->
                events += "discard:$captureId"
                if (shouldFail) error("Injected discard failure")
            },
        ) { request ->
            events += request::class.simpleName.orEmpty()
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()
        fixture.coordinator.discardRemaining()

        assertEquals("brain:1", fixture.coordinator.state.value?.itemId)
        assertEquals(BrainDumpStatusKind.Error, fixture.coordinator.state.value?.status?.kind)
        assertTrue(fixture.coordinator.state.value?.status?.canRetry == true)
        assertEquals(listOf("discard:7"), events)

        shouldFail = false
        fixture.coordinator.retry()

        assertEquals(listOf("discard:7", "discard:7", "close:false"), events)
        assertNull(fixture.coordinator.state.value)
    }

    @Test
    fun consecutiveSkipsFlushInOrderAndKeepOnlyNewestSkipUndoable() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            commits += request
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = commits.size == 2,
            )
        }

        fixture.coordinator.skip()
        fixture.coordinator.skip()

        assertEquals(listOf("brain:1"), commits.map { it.sourceKey })
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
        assertTrue(fixture.coordinator.state.value?.status?.canUndo == true)

        fixture.coordinator.closeCompletion()

        assertEquals(listOf("brain:1", "brain:2"), commits.map { it.sourceKey })
        assertEquals(2, commits.size)
    }

    @Test
    fun incompleteReminderPrimaryRoutesToRequiredTimeSetupWithoutCommitRequest() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val reminder = brainDumpSuggestion("brain:1").copy(
            suggestedType = SuggestedItemType.Reminder,
            suggestedReminderAt = null,
        )
        val fixture = coordinatorFixture(itemCount = 1, items = listOf(reminder)) { request ->
            commits += request
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.commitPrimary()

        assertEquals(BrainDumpStage.ReminderSetup, fixture.coordinator.state.value?.stage)
        assertNull(fixture.coordinator.state.value?.draft?.scheduledAt)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun reminderNotificationWarningSurvivesDraftNavigationLaterStatusAndCompletion() = runBlocking {
        val reminder = brainDumpSuggestion("brain:1").copy(
            suggestedType = SuggestedItemType.Reminder,
            suggestedReminderAt = 1_800_000_000_000L,
        )
        val note = brainDumpSuggestion("brain:2")
        val fixture = coordinatorFixture(itemCount = 2, items = listOf(reminder, note)) { request ->
            when (request) {
                is BrainDumpCommitRequest.SaveReminder -> BrainDumpActionResult(
                    status = BrainDumpActionStatus.Applied,
                    notificationScheduled = false,
                )
                else -> BrainDumpActionResult(
                    status = BrainDumpActionStatus.Applied,
                    sessionCompleted = true,
                )
            }
        }

        fixture.coordinator.commitPrimary()
        assertEquals(BrainDumpStatusMessage.NotificationAttention, fixture.coordinator.state.value?.warning?.message)
        assertFalse(fixture.coordinator.state.value?.warning?.canRetry == true)

        fixture.coordinator.edit()
        fixture.coordinator.updateDraft(requireNotNull(fixture.coordinator.state.value?.draft).copy(title = "Edited"))
        fixture.coordinator.stepBack()
        fixture.coordinator.skip()
        assertEquals(BrainDumpStatusKind.PendingSkip, fixture.coordinator.state.value?.status?.kind)
        assertEquals(BrainDumpStatusMessage.NotificationAttention, fixture.coordinator.state.value?.warning?.message)

        fixture.coordinator.undoSkip()
        fixture.coordinator.commitPrimary()
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
        assertEquals(BrainDumpStatusMessage.NotificationAttention, fixture.coordinator.state.value?.warning?.message)
    }

    @Test
    fun skipAdvancesOptimisticallyAndUndoReturnsToSkippedItem() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            commits += request
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()

        assertEquals("brain:2", fixture.coordinator.state.value?.itemId)
        assertTrue(fixture.coordinator.state.value?.status?.canUndo == true)
        assertTrue(commits.isEmpty())

        fixture.coordinator.undoSkip()

        assertEquals("brain:1", fixture.coordinator.state.value?.itemId)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun nextCommittedActionFlushesSkipBeforeSavingCurrentItem() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            commits += request
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = commits.size == 2,
            )
        }

        fixture.coordinator.skip()
        fixture.coordinator.commitPrimary()

        assertTrue(commits[0] is BrainDumpCommitRequest.Skip)
        assertTrue(commits[1] is BrainDumpCommitRequest.SaveNote)
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
    }

    @Test
    fun failedCommitKeepsDraftAndExposesRetry() = runBlocking {
        var fail = true
        val fixture = coordinatorFixture(itemCount = 1) {
            if (fail) error("Injected write failure")
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = true,
            )
        }
        val edited = requireNotNull(fixture.coordinator.state.value?.draft)
            .copy(title = "Edited title")
        fixture.coordinator.updateDraft(edited)

        fixture.coordinator.commitPrimary()

        assertEquals("Edited title", fixture.coordinator.state.value?.draft?.title)
        assertEquals(BrainDumpStatusKind.Error, fixture.coordinator.state.value?.status?.kind)
        assertTrue(fixture.coordinator.state.value?.status?.canRetry == true)

        fail = false
        fixture.coordinator.retry()
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
    }

    @Test
    fun finishLaterFlushesPendingSkipBeforeClosingResumableSession() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = coordinatorFixture(
            itemCount = 2,
            onClose = { resumable -> events += "close:$resumable" },
        ) { request ->
            events += if (request is BrainDumpCommitRequest.Skip) "Skip" else "Other"
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()
        fixture.coordinator.finishLater()

        assertEquals(listOf("Skip", "close:true"), events)
    }

    @Test
    fun concurrentPrimaryCommitsRunOnlyOneDurableWrite() = runBlocking {
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 1) { request ->
            commits += request
            commitStarted.complete(Unit)
            releaseCommit.await()
            BrainDumpActionResult(BrainDumpActionStatus.Applied, sessionCompleted = true)
        }

        val first = async { fixture.coordinator.commitPrimary() }
        commitStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.commitPrimary() }

        try {
            assertEquals(1, commits.size)
        } finally {
            releaseCommit.complete(Unit)
            first.await()
            second.await()
        }
    }

    @Test
    fun closeCompletionIsRejectedWhileDurableCommitIsRunning() = runBlocking {
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val closes = mutableListOf<Boolean>()
        val fixture = coordinatorFixture(itemCount = 1, onClose = closes::add) {
            commitStarted.complete(Unit)
            releaseCommit.await()
            BrainDumpActionResult(BrainDumpActionStatus.Applied, sessionCompleted = true)
        }

        val commit = async { fixture.coordinator.commitPrimary() }
        commitStarted.await()
        val close = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.closeCompletion() }

        try {
            assertTrue(closes.isEmpty())
        } finally {
            releaseCommit.complete(Unit)
            commit.await()
            close.await()
        }
    }

    @Test
    fun cancellationDoesNotBecomeRetryableSaveFailure() = runBlocking {
        val fixture = coordinatorFixture(itemCount = 1) {
            throw CancellationException("Cancelled write")
        }

        try {
            fixture.coordinator.commitPrimary()
            fail("Expected cancellation to be rethrown")
        } catch (_: CancellationException) {
            assertTrue(fixture.coordinator.state.value?.status?.canRetry != true)
            assertTrue(fixture.coordinator.state.value?.actionInProgress == false)
        }
    }

    @Test
    fun failedSkipFlushDoesNotOfferUndo() = runBlocking {
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            if (request is BrainDumpCommitRequest.Skip) error("Injected skip failure")
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()
        fixture.coordinator.commitPrimary()
        fixture.coordinator.undoSkip()

        assertEquals("brain:1", fixture.coordinator.state.value?.itemId)
        assertTrue(fixture.coordinator.state.value?.status?.canUndo != true)
        assertTrue(fixture.coordinator.state.value?.status?.canRetry == true)
    }

    @Test
    fun startRejectsReplacementWhilePreviousSkipRemainsPending() = runBlocking {
        val independentScope = CoroutineScope(SupervisorJob())
        val fixture = independentScope.coordinatorFixture(itemCount = 2) {
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }
        fixture.coordinator.skip()

        try {
            fixture.coordinator.start(
                captureId = 8L,
                items = listOf(brainDumpSuggestion("new:1")),
                spaces = listOf(CaptureSpaceOption(null, "Inbox")),
                storedOutcomes = mapOf("new:1" to BrainDumpItemOutcome.Pending),
            )
            fail("Expected an unresolved skip to prevent replacement")
        } catch (_: IllegalStateException) {
            assertEquals("brain:2", fixture.coordinator.state.value?.itemId)
        } finally {
            independentScope.cancel()
        }
    }
}

private data class CoordinatorFixture(
    val coordinator: BrainDumpFlowCoordinator,
)

private fun CoroutineScope.coordinatorFixture(
    itemCount: Int,
    onClose: (Boolean) -> Unit = {},
    discardRemaining: suspend (Long) -> Unit = {},
    items: List<BrainDumpSuggestion> = (1..itemCount).map { ordinal ->
        brainDumpSuggestion("brain:$ordinal")
    },
    commit: suspend (BrainDumpCommitRequest) -> BrainDumpActionResult,
): CoordinatorFixture {
    val coordinator = BrainDumpFlowCoordinator(
        scope = this,
        expiryDelay = { suspendCancellableCoroutine<Unit> { } },
        commit = commit,
        discardRemaining = discardRemaining,
        onClose = onClose,
    )
    coordinator.start(
        captureId = 7L,
        items = items,
        spaces = listOf(CaptureSpaceOption(null, "Inbox")),
        storedOutcomes = items.associate { it.id to BrainDumpItemOutcome.Pending },
    )
    return CoordinatorFixture(coordinator)
}

private fun brainDumpSuggestion(id: String) = BrainDumpSuggestion(
    id = id,
    rawText = "thought $id",
    title = "Thought $id",
    suggestedType = SuggestedItemType.Note,
    suggestedSpaceName = "Inbox",
    confidence = 0.8f,
    tinyNextAction = "Review it",
    reason = "This reads like something to keep.",
)
