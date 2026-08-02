package com.orbit.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.export.LocalDataBackupCodec
import com.orbit.app.data.export.LocalDataValidationException
import com.orbit.app.data.export.LocalDataRestoreException
import com.orbit.app.data.export.LocalRestorePlan
import com.orbit.app.data.local.StarterSpaces
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    val restoreMessage: String? = null,
    val errorMessage: String? = null,
)

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
                .onFailure {
                    _uiState.value = LocalDataToolsUiState(errorMessage = "Export could not be created.")
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
                        errorMessage = exception.safeRestoreMessage(),
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
                    val schedulingNote = if (result.remindersReconciled) {
                        ""
                    } else {
                        " Reminder scheduling needs a device check."
                    }
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            restorePlan = null,
                            restoreMessage =
                                "Restore complete: ${result.restoredCounts.visibleItems} items." +
                                    schedulingNote,
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = exception.safeRestoreMessage(),
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
                        container.database.reminderDao().deleteAll()
                        container.database.brainDumpDao().deleteAllItems()
                        container.database.brainDumpDao().deleteAllSessions()
                        container.database.noteDao().deleteAll()
                        container.database.taskDao().deleteAll()
                        container.database.captureDao().deleteAll()
                        container.database.spaceDao().deleteAll()
                        container.database.spaceDao().insertAll(
                            StarterSpaces.entities(System.currentTimeMillis()),
                        )
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        restoreMessage = container.applicationContext.getString(
                            com.orbit.app.R.string.settings_reset_complete,
                        ),
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        errorMessage = container.applicationContext.getString(
                            com.orbit.app.R.string.settings_reset_failed,
                        ),
                    )
                }
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

private fun Throwable.safeRestoreMessage(): String =
    when (this) {
        is LocalDataValidationException,
        is LocalDataRestoreException,
        -> message?.takeIf { it.isNotBlank() }
        else -> null
    } ?: "Restore could not be completed. Existing data was kept."
