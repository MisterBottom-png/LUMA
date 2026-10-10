package com.orbit.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
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
) {
    /** A restore finished but reminders could not all be set up on this phone. */
    val canRetryReminderSetup: Boolean
        get() = (restoreMessage as? LocalDataToolsMessage.RestoreCompleted)?.needsReminderDeviceCheck == true
}

sealed interface LocalDataToolsMessage {
    data object ExportFailed : LocalDataToolsMessage
    data object ExportUnverified : LocalDataToolsMessage
    data object RestoreFileInvalid : LocalDataToolsMessage
    data object RestoreFailed : LocalDataToolsMessage
    data object ResetCompleted : LocalDataToolsMessage
    data object ResetFailed : LocalDataToolsMessage
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

class LocalDataToolsViewModel(private val container: OrbitContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalDataToolsUiState())
    val uiState: StateFlow<LocalDataToolsUiState> = _uiState.asStateFlow()

    fun exportJson(destination: Uri?) {
        if (destination == null) return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    container.localDataExporter.exportJson(destination)
                }
            }
                .onSuccess {
                    _uiState.value = LocalDataToolsUiState(exportCompleted = true)
                }
                .onFailure { failure ->
                    _uiState.value = LocalDataToolsUiState(
                        errorMessage = if (failure is LocalDataExportVerificationException) {
                            LocalDataToolsMessage.ExportUnverified
                        } else {
                            LocalDataToolsMessage.ExportFailed
                        },
                    )
                }
        }
    }

    fun restoreFileSelected(uri: Uri?) {
        if (uri == null || _uiState.value.isPreparingRestore || _uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isPreparingRestore = true, restorePlan = null, errorMessage = null)
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
        if (_uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            runCatching { container.localDataRestorer.restore(plan) }
                .onSuccess { result ->
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
            _uiState.update { it.copy(isResetting = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    // Cancel every scheduled notification first so none can fire
                    // after the rows they reference are gone.
                    container.reminderRepository.observeAll().first().forEach { reminder ->
                        runCatching { container.reminderScheduler.cancel(reminder.id) }
                    }
                    container.database.withTransaction {
                        container.database.captureSuggestionDao().deleteAll()
                        container.database.labelDao().deleteAllNoteLabels()
                        container.database.labelDao().deleteAllTaskLabels()
                        container.database.labelDao().deleteAllReminderLabels()
                        container.database.reminderDao().deleteAll()
                        container.database.brainDumpDao().deleteAllItems()
                        container.database.brainDumpDao().deleteAllSessions()
                        container.database.noteDao().deleteAll()
                        container.database.taskDao().deleteAll()
                        container.database.captureDao().deleteAll()
                        container.database.labelDao().deleteAllLabels()
                        container.database.spaceDao().deleteAll()
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        restoreMessage = LocalDataToolsMessage.ResetCompleted,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        errorMessage = LocalDataToolsMessage.ResetFailed,
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
            val failures = runCatching {
                withContext(Dispatchers.IO) {
                    reconcileReminders(
                        dao = container.database.reminderDao(),
                        scheduler = container.reminderScheduler,
                        now = System.currentTimeMillis(),
                        deliver = { id, time, missed ->
                            ReminderNotifier.showReminderNotification(
                                context = container.applicationContext,
                                reminderId = id,
                                expectedNotificationTime = time,
                                deliveredBy = ReminderDeliveryPath.Reconcile,
                                missed = missed,
                            )
                        },
                        deliverSummary = { ReminderNotifier.showMissedSummary(container.applicationContext, it) },
                    ).failures
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
