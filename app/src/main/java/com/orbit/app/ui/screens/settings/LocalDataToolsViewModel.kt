package com.orbit.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.export.LocalDataBackupCodec
import com.orbit.app.data.export.LocalDataExportVerificationException
import com.orbit.app.data.export.LocalDataValidationException
import com.orbit.app.data.export.LocalRestorePlan
import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.orbit.app.reminders.ReminderDeliveryPath
import com.orbit.app.reminders.ReminderNotifier
import com.orbit.app.reminders.reconcileReminders
import com.orbit.app.reminders.ReminderCapabilities
import com.orbit.app.reminders.ReminderRescheduleWorker
import com.orbit.app.data.local.LocalDataReset
import androidx.core.app.NotificationManagerCompat
import com.orbit.app.reminders.ReminderDeliveryOutcome
import com.orbit.app.domain.capture.LinkedCaptureOutcomes
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalDataToolsUiState(
    val isExporting: Boolean = false,
    val isPreparingRestore: Boolean = false,
    val isRestoring: Boolean = false,
    val isResetting: Boolean = false,
    val exportCompleted: Boolean = false,
    val restorePlan: LocalRestorePlan? = null,
    val restoreMessage: LocalDataToolsMessage? = null,
    val errorMessage: LocalDataToolsMessage? = null,
    val isRetryingReminderSetup: Boolean = false,
    /** When this phone last made an export; null if never. */
    val lastExportAt: Long? = null,
) {
    /** A restore finished but reminders could not all be set up on this phone. */
    val canRetryReminderSetup: Boolean
        get() = (restoreMessage as? LocalDataToolsMessage.RestoreCompleted)?.needsReminderDeviceCheck == true ||
            // Still failing: the user can fix settings and try again (review N035).
            restoreMessage == LocalDataToolsMessage.ReminderSetupStillFailing
}

sealed interface LocalDataToolsMessage {
    data object ExportFailed : LocalDataToolsMessage
    data object ExportUnverified : LocalDataToolsMessage
    data object ExportTooLarge : LocalDataToolsMessage
    data object RestoreFileInvalid : LocalDataToolsMessage
    data object RestoreFailed : LocalDataToolsMessage
    data object ResetCompleted : LocalDataToolsMessage
    data object ResetFailed : LocalDataToolsMessage
    data object ResetIncomplete : LocalDataToolsMessage
    data object ReminderSetupRestored : LocalDataToolsMessage
    data object ReminderSetupStillFailing : LocalDataToolsMessage
    data class RestoreCompleted(
        val visibleItemCount: Int,
        val needsReminderDeviceCheck: Boolean,
    ) : LocalDataToolsMessage
}

internal fun restoreCompletionMessage(
    visibleItemCount: Int,
    remindersReconciled: Boolean,
): LocalDataToolsMessage.RestoreCompleted = LocalDataToolsMessage.RestoreCompleted(
    visibleItemCount = visibleItemCount,
    needsReminderDeviceCheck = !remindersReconciled,
)

internal fun restoreFailureMessage(failure: Throwable): LocalDataToolsMessage =
    if (failure is LocalDataValidationException) {
        LocalDataToolsMessage.RestoreFileInvalid
    } else {
        LocalDataToolsMessage.RestoreFailed
    }

/** Which button a message belongs next to. */
internal enum class LocalDataArea { Export, Restore, Reset }

internal fun LocalDataToolsMessage.area(): LocalDataArea = when (this) {
    LocalDataToolsMessage.ExportFailed, LocalDataToolsMessage.ExportUnverified, LocalDataToolsMessage.ExportTooLarge -> LocalDataArea.Export
    LocalDataToolsMessage.ResetCompleted, LocalDataToolsMessage.ResetFailed, LocalDataToolsMessage.ResetIncomplete -> LocalDataArea.Reset
    else -> LocalDataArea.Restore
}

class LocalDataToolsViewModel(private val container: OrbitContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalDataToolsUiState(lastExportAt = container.lastExportMemory.lastExportAt()))
    val uiState: StateFlow<LocalDataToolsUiState> = _uiState.asStateFlow()

    fun exportJson(destination: Uri?) {
        if (destination == null) return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            // A new action clears the last one's messages.
            _uiState.update { it.copy(isExporting = true, errorMessage = null, restoreMessage = null, exportCompleted = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    container.localDataExporter.exportJson(destination)
                }
            }
                .onSuccess {
                    val at = System.currentTimeMillis()
                    container.lastExportMemory.recordExport(at)
                    _uiState.value = LocalDataToolsUiState(exportCompleted = true, lastExportAt = at)
                }
                .onFailure { failure ->
                    _uiState.value = LocalDataToolsUiState(
                        lastExportAt = container.lastExportMemory.lastExportAt(),
                        errorMessage = when (failure) {
                            is com.orbit.app.data.export.LocalDataExportTooLargeException -> LocalDataToolsMessage.ExportTooLarge
                            is LocalDataExportVerificationException -> LocalDataToolsMessage.ExportUnverified
                            else -> LocalDataToolsMessage.ExportFailed
                        },
                    )
                }
        }
    }

    fun restoreFileSelected(uri: Uri?) {
        if (uri == null || _uiState.value.isPreparingRestore || _uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isPreparingRestore = true, restorePlan = null, errorMessage = null, restoreMessage = null, exportCompleted = false)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = requireNotNull(
                        container.applicationContext.contentResolver.openInputStream(uri),
                    ) { "The selected file could not be opened." }
                    input.use(InputStream::readRestoreText)
                }
            }.mapCatching { json ->
                container.localDataRestorer.prepare(json)
                    ?: error("No restore file was selected.")
            }.onSuccess { plan ->
                _uiState.update {
                    it.copy(isPreparingRestore = false, restorePlan = plan)
                }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isPreparingRestore = false,
                        errorMessage = restoreFailureMessage(exception),
                    )
                }
            }
        }
    }

    fun cancelRestore() {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(restorePlan = null) }
    }

    fun confirmRestore() {
        val plan = _uiState.value.restorePlan ?: return
        // N031: a restore never runs while a reset does, or twice.
        if (_uiState.value.isRestoring || _uiState.value.isResetting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            runCatching { container.localDataRestorer.restore(plan) }
                .onSuccess { result ->
                    // Task ids now name the restored tasks, not the ones remembered here.
                    container.archivedTaskStatusMemory.clear()
                    // N036: the last receipt named a thought from before the restore.
                    runCatching { container.appSettingsRepository.edit { it.copy(lastReceiptCaptureId = null) } }
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            restorePlan = null,
                            restoreMessage = restoreCompletionMessage(
                                visibleItemCount = result.restoredCounts.visibleItems,
                                remindersReconciled = result.remindersReconciled,
                            ),
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = restoreFailureMessage(exception),
                        )
                    }
                }
        }
    }

    fun resetAllData() {
        if (_uiState.value.isRestoring || _uiState.value.isResetting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true, errorMessage = null, restoreMessage = null, exportCompleted = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val context = container.applicationContext
                    LocalDataReset(
                        database = container.database,
                        scheduler = container.reminderScheduler,
                        settings = container.appSettingsRepository,
                        geminiKey = container.geminiApiKeyStore,
                        clearPreferences = {
                            container.archivedTaskStatusMemory.clear()
                            container.lastExportMemory.clear()
                            ReminderCapabilities.clearDeliveryHistory(context)
                        },
                        cancelNotifications = { NotificationManagerCompat.from(context).cancelAll() },
                        onItemsKept = { ReminderRescheduleWorker.enqueue(context) },
                    ).run()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        lastExportAt = null,
                        restoreMessage = LocalDataToolsMessage.ResetCompleted,
                    )
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        errorMessage = if (failure is com.orbit.app.data.local.LocalDataResetIncomplete) {
                            LocalDataToolsMessage.ResetIncomplete
                        } else {
                            LocalDataToolsMessage.ResetFailed
                        },
                    )
                }
            }
        }
    }

    /** Recovery action after a restore whose reminder setup did not fully succeed. */
    fun retryReminderSetup() {
        if (_uiState.value.isRetryingReminderSetup) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRetryingReminderSetup = true) }
            // RS1: success only when every reminder is armed and Android lets them ring;
            // a notification Android blocked counts as a failure, not as set up.
            val context = container.applicationContext
            val failures = runCatching {
                withContext(Dispatchers.IO) {
                    var blocked = 0
                    val result = reconcileReminders(
                        dao = container.database.reminderDao(),
                        scheduler = container.reminderScheduler,
                        now = System.currentTimeMillis(),
                        deliver = { id, time, missed ->
                            ReminderNotifier.showReminderNotification(
                                context = context,
                                reminderId = id,
                                expectedNotificationTime = time,
                                deliveredBy = ReminderDeliveryPath.Reconcile,
                                missed = missed,
                            ).also { if (it == ReminderDeliveryOutcome.Blocked) blocked += 1 }
                        },
                        deliverSummary = {
                            ReminderNotifier.showMissedSummary(context, it)
                                .also { outcome -> if (outcome == ReminderDeliveryOutcome.Blocked) blocked += 1 }
                        },
                        // Thoughts waiting on a reminder that is now armed are set reminders again.
                        linkedCaptures = LinkedCaptureOutcomes(
                            captureRepository = container.captureRepository,
                            transaction = RoomCaptureFinalizationTransaction(container.database),
                            canNotify = { canRing(context) },
                        ),
                    )
                    result.failures + blocked + if (canRing(context)) 0 else 1
                }
            }.getOrElse { 1 }
            _uiState.update {
                it.copy(
                    isRetryingReminderSetup = false,
                    restoreMessage = if (failures == 0) {
                        LocalDataToolsMessage.ReminderSetupRestored
                    } else {
                        LocalDataToolsMessage.ReminderSetupStillFailing
                    },
                )
            }
        }
    }

    private fun canRing(context: android.content.Context): Boolean =
        ReminderCapabilities.notificationsAllowed(context) && ReminderCapabilities.reminderChannelEnabled(context)

    fun messageShown() {
        _uiState.update { it.copy(errorMessage = null, restoreMessage = null) }
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocalDataToolsViewModel::class.java))
            return LocalDataToolsViewModel(container) as T
        }
    }
}

private fun InputStream.readRestoreText(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > LocalDataBackupCodec.MaximumInputBytes) {
            throw LocalDataValidationException("The selected export is too large.")
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
