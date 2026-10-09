package com.orbit.app.ui.screens.home

import com.orbit.app.domain.usecase.BrainDumpActionResult
import com.orbit.app.domain.usecase.BrainDumpActionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BrainDumpSkipUndoControllerTest {
    @Test
    fun beginExposesPendingAndUndoPreventsCommit() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val commits = mutableListOf<Pair<Long, String>>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { captureId, sourceKey ->
                commits += captureId to sourceKey
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        assertEquals("brain:1", controller.pending.value?.sourceKey)

        assertTrue(controller.undo())
        gate.complete(Unit)
        yield()

        assertTrue(commits.isEmpty())
        assertNull(controller.pending.value)
    }

    @Test
    fun concurrentFlushesCommitOnlyOnceBeforeTheNextAction() = runBlocking {
        val expiryGate = CompletableDeferred<Unit>()
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        var commitCount = 0
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { expiryGate.await() },
            commitSkip = { _, _ ->
                commitCount += 1
                commitStarted.complete(Unit)
                releaseCommit.await()
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        val first = async(Dispatchers.Default) { controller.flush() }
        commitStarted.await()
        val secondEnteredFlush = CompletableDeferred<Unit>()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            secondEnteredFlush.complete(Unit)
            controller.flush()
        }
        secondEnteredFlush.await()
        assertTrue(second.isActive)
        releaseCommit.complete(Unit)
        val commits = listOf(first.await(), second.await())

        assertEquals(1, commits.count { it?.result?.status == BrainDumpActionStatus.Applied })
        assertEquals(1, commitCount)
    }

    @Test
    fun expiryCommitsFinalSkipAndReportsFinalFlag() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val commits = mutableListOf<BrainDumpSkipCommit>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ ->
                BrainDumpActionResult(
                    status = BrainDumpActionStatus.Applied,
                    sessionCompleted = true,
                )
            },
            onCommitted = commits::add,
        )

        controller.begin(7L, "brain:1", isFinalItem = true)
        gate.complete(Unit)
        yield()

        assertTrue(commits.single().pending.isFinalItem)
        assertTrue(commits.single().result.sessionCompleted)
    }

    @Test
    fun processLossBeforeFlushLeavesSkipUncommitted() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var commitCount = 0
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ ->
                commitCount += 1
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        controller.cancelWithoutCommit()

        assertEquals(0, commitCount)
        assertNull(controller.pending.value)
    }

    @Test
    fun expiryFailureKeepsPendingSkipAndReportsFailure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val failures = mutableListOf<String>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ -> error("Injected skip failure") },
            onCommitFailed = { pending, _ -> failures += pending.sourceKey },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        gate.complete(Unit)
        yield()

        assertEquals(listOf("brain:1"), failures)
        assertEquals("brain:1", controller.pending.value?.sourceKey)
    }

    @Test
    fun undoReturnsFalseWhenExpiryCommitHasStarted() = runBlocking {
        val expiryGate = CompletableDeferred<Unit>()
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val committed = CompletableDeferred<BrainDumpSkipCommit>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { expiryGate.await() },
            commitSkip = { _, _ ->
                commitStarted.complete(Unit)
                releaseCommit.await()
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
            onCommitted = committed::complete,
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        expiryGate.complete(Unit)
        commitStarted.await()

        assertFalse(controller.undo())
        releaseCommit.complete(Unit)

        assertEquals("brain:1", committed.await().pending.sourceKey)
        assertNull(controller.pending.value)
    }

    @Test
    fun flushFailureKeepsPendingSkipAndReportsFailure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val failures = mutableListOf<String>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ -> error("Injected skip failure") },
            onCommitFailed = { pending, _ -> failures += pending.sourceKey },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        runCatching { controller.flush() }

        assertEquals(listOf("brain:1"), failures)
        assertEquals("brain:1", controller.pending.value?.sourceKey)
    }

    @Test
    fun concurrentBeginsAllowOnlyOnePendingSkip() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ -> BrainDumpActionResult(BrainDumpActionStatus.Applied) },
        )
        val executor = Executors.newFixedThreadPool(32)
        val start = CountDownLatch(1)

        try {
            val results = (1L..32L).map { captureId ->
                executor.submit<Boolean> {
                    start.await()
                    runCatching {
                        controller.begin(captureId, "brain:$captureId", isFinalItem = false)
                        true
                    }.getOrDefault(false)
                }
            }

            start.countDown()

            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(controller.pending.value != null)
        } finally {
            controller.cancelWithoutCommit()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellationRethrowsWithoutReportingSkipFailure() = runBlocking {
        val failures = mutableListOf<String>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { suspendCancellableCoroutine<Unit> { } },
            commitSkip = { _, _ -> throw CancellationException("Cancelled skip") },
            onCommitFailed = { pending, _ -> failures += pending.sourceKey },
        )
        controller.begin(7L, "brain:1", isFinalItem = false)

        try {
            controller.flush()
            fail("Expected cancellation to be rethrown")
        } catch (_: CancellationException) {
            assertTrue(failures.isEmpty())
            assertEquals("brain:1", controller.pending.value?.sourceKey)
        } finally {
            controller.cancelWithoutCommit()
        }
    }
}
