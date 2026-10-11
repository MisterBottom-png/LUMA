package com.orbit.app.ui.screens.settings

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.orbit.app.OrbitContainer
import com.orbit.app.R
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.integrations.gemini.GeminiApiResult
import com.orbit.app.integrations.gemini.GeminiApiErrorKind
import com.orbit.app.ui.localization.effectiveAppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val hasKey: Boolean = false,
    val isSavingKey: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val connectionSucceeded: Boolean? = null,
    val isClearingLearning: Boolean = false,
    val learningClearSucceeded: Boolean? = null,
    val learnedRules: List<LearnedRuleEntity> = emptyList(),
    /** A learned rule could not be changed; shown on the Learning page. */
    val learnedRuleFailed: Boolean = false,
)

class AiSettingsViewModel(private val container: OrbitContainer) : ViewModel() {
    // Resolved on every use: this ViewModel outlives the recreation that follows a
    // language change, so a cached context would keep producing the old language.
    private val localizedContext: android.content.Context
        get() {
            val base = container.applicationContext
            return base.createConfigurationContext(
                Configuration(base.resources.configuration).apply {
                    setLocale(effectiveAppLocale(base))
                },
            )
        }
    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshKeyState()
        viewModelScope.launch {
            container.learnedRuleRepository.observeAll().collect { rules ->
                _uiState.update { it.copy(learnedRules = rules) }
            }
        }
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
                            connectionMessage = localized(R.string.settings_gemini_key_saved),
                            connectionSucceeded = true,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSavingKey = false,
                            connectionMessage = localized(R.string.settings_gemini_key_save_failed),
                            connectionSucceeded = false,
                        )
                    }
                }
        }
    }

    fun deleteKey() {
        viewModelScope.launch {
            runCatching { container.geminiApiKeyStore.deleteKey() }
                .onSuccess {
                    // Keep everything else (for example the learned rules list) in place.
                    _uiState.update {
                        it.copy(
                            hasKey = false,
                            connectionMessage = localized(R.string.settings_gemini_key_removed),
                            connectionSucceeded = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            connectionMessage = localized(R.string.settings_gemini_key_remove_failed),
                            connectionSucceeded = false,
                        )
                    }
                }
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
                        connectionMessage = localized(GeminiApiErrorKind.MissingKey.settingsMessageRes()),
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
                            connectionMessage = localized(
                                R.string.settings_gemini_connection_succeeded,
                                testedModels,
                            ),
                            connectionSucceeded = true,
                        )
                    }
                }

                else -> {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            hasKey = true,
                            connectionMessage = localized(failure.error.kind.settingsMessageRes()),
                            connectionSucceeded = false,
                        )
                    }
                }
            }
        }
    }

    fun clearLearningData() {
        if (_uiState.value.isClearingLearning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingLearning = true, learningClearSucceeded = null) }
            runCatching {
                container.database.withTransaction {
                    container.database.aiCorrectionHistoryDao().deleteAll()
                    container.database.learnedRuleDao().deleteAll()
                    container.database.personMemoryDao().deleteAll()
                    container.database.projectMemoryDao().deleteAll()
                    container.database.spaceAliasMemoryDao().deleteAll()
                    container.database.aiSuggestionHistoryDao().deleteAll()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(isClearingLearning = false, learningClearSucceeded = true)
                }
            }.onFailure {
                _uiState.update {
                    it.copy(isClearingLearning = false, learningClearSucceeded = false)
                }
            }
        }
    }

    fun updateLearnedRule(rule: LearnedRuleEntity) {
        viewModelScope.launch {
            runCatching { container.learnedRuleRepository.update(rule) }
                .onSuccess { _uiState.update { it.copy(learnedRuleFailed = false) } }
                .onFailure { reportLearnedRuleFailure() }
        }
    }

    fun deleteLearnedRule(rule: LearnedRuleEntity) {
        viewModelScope.launch {
            runCatching { container.learnedRuleRepository.delete(rule) }
                .onSuccess { _uiState.update { it.copy(learnedRuleFailed = false) } }
                .onFailure { reportLearnedRuleFailure() }
        }
    }

    private fun reportLearnedRuleFailure() {
        _uiState.update { it.copy(learnedRuleFailed = true) }
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AiSettingsViewModel::class.java))
            return AiSettingsViewModel(container) as T
        }
    }

    private fun localized(@StringRes resId: Int, vararg formatArgs: Any): String =
        localizedContext.getString(resId, *formatArgs)
}

@StringRes
internal fun GeminiApiErrorKind.settingsMessageRes(): Int = when (this) {
    GeminiApiErrorKind.MissingKey -> R.string.settings_gemini_error_missing_key
    GeminiApiErrorKind.BadKey -> R.string.settings_gemini_error_bad_key
    GeminiApiErrorKind.RateLimited -> R.string.settings_gemini_error_rate_limited
    GeminiApiErrorKind.Timeout -> R.string.settings_gemini_error_timeout
    GeminiApiErrorKind.NoInternet -> R.string.settings_gemini_error_no_internet
    GeminiApiErrorKind.InvalidResponse -> R.string.settings_gemini_error_invalid_response
    GeminiApiErrorKind.SafetyBlocked -> R.string.settings_gemini_error_safety_blocked
    GeminiApiErrorKind.Server -> R.string.settings_gemini_error_server
    GeminiApiErrorKind.ModelNotFound -> R.string.settings_gemini_error_model_not_found
    GeminiApiErrorKind.Unknown -> R.string.settings_gemini_error_unknown
}
