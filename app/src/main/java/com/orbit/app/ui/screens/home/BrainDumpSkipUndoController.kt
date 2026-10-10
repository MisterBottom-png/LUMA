package com.orbit.app.ui.screens.home

import com.orbit.app.domain.usecase.BrainDumpActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PendingBrainDumpSkip(
    val captureId: Long,
    val sourceKey: String,
    val isFinalItem: Boolean,
)

internal data class BrainDumpSkipCommit(
    val pending: PendingBrainDumpSkip,
    val result: BrainDumpActionResult,
)

internal class BrainDumpSkipUndoController(
    private val scope: CoroutineScope,
    private val expiryDelay: suspend (Long) -> Unit = { delay(it) },
    private val commitSkip: suspend (Long, String) -> BrainDumpActionResult,
    private val onCommitted: (BrainDumpSkipCommit) -> Unit = {},
    private val onCommitFailed: (PendingBrainDumpSkip, Throwable) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val stateLock = Any()
    private val _pending = MutableStateFlow<PendingBrainDumpSkip?>(null)
    val pending: StateFlow<PendingBrainDumpSkip?> = _pending.asStateFlow()
    private var expiryJob: Job? = null
    private var commitInProgress = false

    fun begin(captureId: Long, sourceKey: String, isFinalItem: Boolean) {
        val value = PendingBrainDumpSkip(captureId, sourceKey, isFinalItem)
        val scheduledExpiry = synchronized(stateLock) {
            check(_pending.value == null) { "Only one Brain Dump skip may be pending" }
            _pending.value = value
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    expiryDelay(UndoWindowMillis)
                    flush(expiryOwner = coroutineContext[Job])?.let(onCommitted)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // The controller has already retained pending state and reported ordinary failures.
                }
            }.also { expiryJob = it }
        }
        scheduledExpiry.start()
    }

    fun undo(): Boolean {
        val (existed, scheduledExpiry) = synchronized(stateLock) {
            if (_pending.value == null || commitInProgress) {
                false to null
            } else {
                val expiry = expiryJob
                expiryJob = null
                _pending.value = null
                true to expiry
            }
        }
        scheduledExpiry?.cancel()
        return existed
    }

    suspend fun flush(): BrainDumpSkipCommit? = flush(expiryOwner = null)

    private suspend fun flush(expiryOwner: Job?): BrainDumpSkipCommit? = mutex.withLock {
        val commitState = synchronized(stateLock) {
            val pending = _pending.value ?: return@withLock null
            if (commitInProgress || (expiryOwner != null && expiryJob !== expiryOwner)) {
                return@withLock null
            }
            commitInProgress = true
            val scheduledExpiry = expiryJob
            if (expiryOwner == null) {
                expiryJob = null
            }
            pending to scheduledExpiry
        }
        val (value, scheduledExpiry) = commitState
        if (expiryOwner == null) {
            scheduledExpiry?.cancel()
        }
        val result = try {
            commitSkip(value.captureId, value.sourceKey)
        } catch (failure: CancellationException) {
            finishCommit(value, expiryOwner, clearPending = false)
            throw failure
        } catch (failure: Throwable) {
            finishCommit(value, expiryOwner, clearPending = false)
            onCommitFailed(value, failure)
            throw failure
        }
        finishCommit(value, expiryOwner, clearPending = true)
        BrainDumpSkipCommit(value, result)
    }

    suspend fun cancelWithoutCommit() {
        val scheduledExpiry = synchronized(stateLock) {
            expiryJob.also { expiryJob = null }
        }
        scheduledExpiry?.cancelAndJoin()
        mutex.withLock {
            synchronized(stateLock) {
                if (!commitInProgress) {
                    _pending.value = null
                }
            }
        }
    }

    private fun finishCommit(
        value: PendingBrainDumpSkip,
        expiryOwner: Job?,
        clearPending: Boolean,
    ) {
        synchronized(stateLock) {
            if (clearPending && _pending.value === value) {
                _pending.value = null
            }
            if (expiryJob === expiryOwner) {
                expiryJob = null
            }
            commitInProgress = false
        }
    }

    private companion object {
        const val UndoWindowMillis = 5_000L
    }
}
