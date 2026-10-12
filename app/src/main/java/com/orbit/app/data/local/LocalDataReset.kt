package com.orbit.app.data.local

import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.security.GeminiApiKeyStore

/**
 * "Reset local data" is a fresh start (review F10, B7, N036): every item, Space and
 * Brain Dump, AI history and corrections, learned rules and memory, all settings
 * (including the last receipt and consent), the Gemini key, remembered preferences,
 * alarms and shown notifications. Afterwards the app opens as on its first launch.
 *
 * Items go first, in one step: if that fails nothing has been deleted and alarms are
 * set up again by [onItemsKept].
 */
class LocalDataReset(
    private val database: OrbitDatabase,
    private val scheduler: ReminderScheduler,
    private val settings: AppSettingsRepository,
    private val geminiKey: GeminiApiKeyStore,
    /** Plain preference files: last export, archived task states, delivery history. */
    private val clearPreferences: () -> Unit,
    /** Shown notifications, including the missed-reminders summary. */
    private val cancelNotifications: () -> Unit,
    private val onItemsKept: () -> Unit = {},
) {
    suspend fun run() {
        database.reminderDao().getAll().forEach { reminder ->
            runCatching { scheduler.cancel(reminder.id) }
        }
        try {
            database.clearAllTables()
        } catch (failure: Exception) {
            onItemsKept()
            throw failure
        }
        // The rest cannot bring deleted items back; each part is tried even if one fails.
        val failures = listOf(
            runCatching { settings.reset() },
            runCatching { geminiKey.deleteKey() },
            runCatching { clearPreferences() },
            runCatching { cancelNotifications() },
        ).mapNotNull { it.exceptionOrNull() }
        failures.firstOrNull()?.let { throw LocalDataResetIncomplete(it) }
    }
}

/** Every item is gone, but a setting, the key or a preference file could not be cleared. */
class LocalDataResetIncomplete(cause: Throwable) : Exception(cause)
