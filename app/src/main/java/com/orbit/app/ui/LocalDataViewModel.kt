package com.orbit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.domain.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalDataViewModel(private val container: OrbitContainer) : ViewModel() {
    val captures = container.captureRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val spaces = container.spaceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = container.noteRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = container.taskRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders = container.reminderRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = container.appSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun insertCapture(entity: CaptureEntity) = viewModelScope.launch {
        container.captureRepository.insert(entity)
    }

    fun updateCapture(entity: CaptureEntity) = viewModelScope.launch {
        container.captureRepository.update(entity)
    }

    fun deleteCapture(id: Long) = viewModelScope.launch {
        container.captureRepository.deleteById(id)
    }

    fun insertSpace(entity: SpaceEntity) = viewModelScope.launch {
        container.spaceRepository.insert(entity)
    }

    fun updateSpace(entity: SpaceEntity) = viewModelScope.launch {
        container.spaceRepository.update(entity)
    }

    fun deleteSpace(id: Long) = viewModelScope.launch {
        container.spaceRepository.deleteById(id)
    }

    fun insertNote(entity: NoteEntity) = viewModelScope.launch {
        container.noteRepository.insert(entity)
    }

    fun updateNote(entity: NoteEntity) = viewModelScope.launch {
        container.noteRepository.update(entity)
    }

    fun deleteNote(id: Long) = viewModelScope.launch {
        container.noteRepository.deleteById(id)
    }

    fun insertTask(entity: TaskEntity) = viewModelScope.launch {
        container.taskRepository.insert(entity)
    }

    fun updateTask(entity: TaskEntity) = viewModelScope.launch {
        container.taskRepository.update(entity)
    }

    fun deleteTask(id: Long) = viewModelScope.launch {
        container.taskRepository.deleteById(id)
    }

    fun insertReminder(entity: ReminderEntity) = viewModelScope.launch {
        container.reminderRepository.insert(entity)
    }

    fun updateReminder(entity: ReminderEntity) = viewModelScope.launch {
        container.reminderRepository.update(entity)
    }

    fun deleteReminder(id: Long) = viewModelScope.launch {
        container.reminderRepository.deleteById(id)
    }

    fun updateSettings(settings: AppSettings) = viewModelScope.launch {
        container.appSettingsRepository.update(settings)
    }

    fun resetSettings() = viewModelScope.launch {
        container.appSettingsRepository.reset()
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocalDataViewModel::class.java))
            return LocalDataViewModel(container) as T
        }
    }
}
