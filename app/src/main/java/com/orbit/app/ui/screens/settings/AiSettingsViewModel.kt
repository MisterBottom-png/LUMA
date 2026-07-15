package com.orbit.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.integrations.gemini.GeminiApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val hasKey: Boolean = false,
    val isSavingKey: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val connectionSucceeded: Boolean? = null,
)

class AiSettingsViewModel(private val container: OrbitContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshKeyState()
    }

    fun refreshKeyState() {
        viewModelScope.launch {
            _uiState.update { it.copy(hasKey = container.geminiApiKeyStore.hasKey()) }
        }
    }

    fun saveKey(apiKey: String) {
        if (_uiState.value.isSavingKey) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSavingKey = true, connectionMessage = null, connectionSucceeded = null)
            }
            runCatching { container.geminiApiKeyStore.saveKey(apiKey) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            hasKey = true,
                            isSavingKey = false,
                            connectionMessage = "Gemini key saved on this device.",
                            connectionSucceeded = true,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSavingKey = false,
                            connectionMessage = "That key could not be saved.",
                            connectionSucceeded = false,
                        )
                    }
                }
        }
    }

    fun deleteKey() {
        viewModelScope.launch {
            container.geminiApiKeyStore.deleteKey()
            _uiState.value = AiSettingsUiState(connectionMessage = "Gemini key removed.")
        }
    }

    fun testConnection(fastModelId: String, reasoningModelId: String) {
        if (_uiState.value.isTestingConnection) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isTestingConnection = true, connectionMessage = null, connectionSucceeded = null)
            }
            val apiKey = container.geminiApiKeyStore.getKey()
            if (apiKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        hasKey = false,
                        connectionMessage = "Add a Gemini API key first. Local mode still works.",
                        connectionSucceeded = false,
                    )
                }
                return@launch
            }

            val fastResult = container.geminiApiClient.testConnection(apiKey, fastModelId)
            val reasoningResult = if (reasoningModelId.equals(fastModelId, ignoreCase = true)) {
                fastResult
            } else {
                container.geminiApiClient.testConnection(apiKey, reasoningModelId)
            }

            val failure = listOf(fastResult, reasoningResult)
                .filterIsInstance<GeminiApiResult.Failure>()
                .firstOrNull()

            when (failure) {
                null -> {
                    val testedModels = if (reasoningModelId.equals(fastModelId, ignoreCase = true)) {
                        fastModelId
                    } else {
                        "$fastModelId and $reasoningModelId"
                    }
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            hasKey = true,
                            connectionMessage = "Gemini connection works for $testedModels.",
                            connectionSucceeded = true,
                        )
                    }
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            hasKey = true,
                            connectionMessage = failure.error.userMessage,
                            connectionSucceeded = false,
                        )
                    }
                }
            }
        }
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AiSettingsViewModel::class.java))
            return AiSettingsViewModel(container) as T
        }
    }
}
