package com.orbit.app.ui.screens.home

import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.ReminderSaveOutcomes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After a reminder is saved from the sort sheet or Brain Dump: asks for the
 * notification permission when Android still needs it, waits for the answer, and
 * only then reads what the save achieved. So "Reminder set" is never shown before
 * the user decided, and "notifications are off" is never shown after they allowed them.
 */
internal class ReminderOutcomeReporter(
    private val outcomes: ReminderSaveOutcomes,
    private val permissionGranted: () -> Boolean,
    private val requestPermission: () -> Unit,
) {
    private var answer: CompletableDeferred<Boolean>? = null

    suspend fun report(reminderId: Long): ReminderSaveOutcome {
        if (!permissionGranted()) {
            val pending = CompletableDeferred<Boolean>()
            answer = pending
            try {
                requestPermission()
                withTimeoutOrNull(PermissionAnswerTimeoutMillis) { pending.await() }
            } finally {
                if (answer === pending) answer = null
            }
        }
        return outcomes.of(reminderId)
    }

    fun onPermissionAnswer(granted: Boolean) {
        answer?.complete(granted)
    }

    private companion object {
        // The system dialog normally answers at once; this only guards against a lost answer.
        const val PermissionAnswerTimeoutMillis = 120_000L
    }
}
