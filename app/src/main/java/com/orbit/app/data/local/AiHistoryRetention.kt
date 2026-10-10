package com.orbit.app.data.local

import androidx.room.withTransaction
import java.util.concurrent.TimeUnit

/**
 * AI suggestion and correction history keeps short snippets of the user's thoughts.
 * It only needs to be recent to be useful, so older entries are removed. Learned
 * rules stay (their link to history is cleared by the foreign key).
 */
object AiHistoryRetention {
    val KeepMillis: Long = TimeUnit.DAYS.toMillis(90)

    /** Returns how many history rows were removed. */
    suspend fun prune(database: OrbitDatabase, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - KeepMillis
        return database.withTransaction {
            database.aiCorrectionHistoryDao().deleteOlderThan(cutoff) +
                database.aiSuggestionHistoryDao().deleteOlderThan(cutoff)
        }
    }
}
