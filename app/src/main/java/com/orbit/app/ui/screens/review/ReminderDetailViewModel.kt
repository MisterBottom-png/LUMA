package com.orbit.app.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReminderDetailUiState(
    val isLoading: Boolean = true,
    val reminder: ReminderEntity? = null,
    val relatedCaptureText: String? = null,
    val relatedTaskTitle: String? = null,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

class ReminderDetailViewModel(
    private val reminderId: Long,
    private val reminderRepository: ReminderRepository,
    private val captureRepository: CaptureRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderDetailUiState())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun complete() {
        val reminder = _uiState.value.reminder ?: return
        viewModelScope.launch {
            runCatching {
                reminderRepository.update(
                    reminder.copy(
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                load()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "The reminder could not be completed.")
                }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { reminderRepository.deleteById(reminderId) }
                .onSuccess { _uiState.update { it.copy(isDeleted = true) } }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(errorMessage = "The reminder could not be deleted.")
                    }
                }
        }
    }

    fun reschedule(dueAt: Long) {
        val reminder = _uiState.value.reminder ?: return
        viewModelScope.launch {
            runCatching {
                reminderRepository.update(
                    reminder.copy(
                        dueAt = dueAt,
                        completedAt = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                load()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "The reminder could not be rescheduled.")
                }
            }
        }
    }

    fun updateNotificationOffset(offsetMinutes: Long) {
        val reminder = _uiState.value.reminder ?: return
        viewModelScope.launch {
            runCatching {
                reminderRepository.update(
                    reminder.copy(
                        notificationOffsetMinutes = offsetMinutes,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                load()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "The notification time could not be updated.")
                }
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        val reminder = _uiState.value.reminder ?: return
        viewModelScope.launch {
            runCatching {
                reminderRepository.update(
                    reminder.copy(
                        notificationEnabled = enabled,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                load()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "Notification delivery could not be updated.")
                }
            }
        }
    }

    fun errorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val reminder = reminderRepository.getById(reminderId)
            val captureText = reminder?.linkedCaptureId
                ?.let { captureRepository.getById(it)?.rawText }
            val taskTitle = reminder?.linkedTaskId
                ?.let { taskRepository.getById(it)?.title }
            _uiState.value = ReminderDetailUiState(
                isLoading = false,
                reminder = reminder,
                relatedCaptureText = captureText,
                relatedTaskTitle = taskTitle,
            )
        }
    }

    class Factory(
        private val reminderId: Long,
        private val reminderRepository: ReminderRepository,
        private val captureRepository: CaptureRepository,
        private val taskRepository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReminderDetailViewModel::class.java))
            return ReminderDetailViewModel(
                reminderId,
                reminderRepository,
                captureRepository,
                taskRepository,
            ) as T
        }
    }
}
