package com.orbit.app.ui.screens.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.StarterSpaceTemplate
import com.orbit.app.data.local.StarterSpaces
import com.orbit.app.data.repository.SpaceRepository
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TutorialSpaceSetupUiState(
    val canConfigure: Boolean = false,
    val selectedTemplateKeys: Set<String> = emptySet(),
    val customNames: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

class TutorialSpaceSetupViewModel(
    private val isReplay: Boolean,
    private val spaceRepository: SpaceRepository,
    private val observationDispatcher: CoroutineContext = Dispatchers.IO,
    private val workerDispatcher: CoroutineContext = Dispatchers.IO,
    private val uiDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TutorialSpaceSetupUiState())
    val uiState = _uiState

    init {
        viewModelScope.launch(observationDispatcher) {
            spaceRepository.observeAll().collectLatest { spaces ->
                _uiState.update { state ->
                    state.copy(canConfigure = !isReplay && spaces.isEmpty())
                }
            }
        }
    }

    fun toggleTemplate(key: String) {
        if (!uiState.value.canConfigure || uiState.value.isSaving) return
        if (StarterSpaces.templates.none { it.key == key }) return
        _uiState.update { state ->
            val selected = state.selectedTemplateKeys.toMutableSet()
            if (!selected.add(key)) selected.remove(key)
            state.copy(selectedTemplateKeys = selected, saveFailed = false)
        }
    }

    fun addCustomName(rawName: String) {
        if (!uiState.value.canConfigure || uiState.value.isSaving) return
        val name = cleanName(rawName)
        if (name.isEmpty()) return
        _uiState.update { state ->
            val selectedNames = selectedTemplates(state.selectedTemplateKeys).map { it.storedName }
            val existingNames = selectedNames + state.customNames
            if (existingNames.any { canonicalName(it) == canonicalName(name) }) state
            else state.copy(customNames = state.customNames + name, saveFailed = false)
        }
    }

    fun removeCustomName(name: String) {
        if (uiState.value.isSaving) return
        _uiState.update { state -> state.copy(customNames = state.customNames - name) }
    }

    fun finish(onComplete: () -> Unit) {
        val state = uiState.value
        if (!state.canConfigure) {
            onComplete()
            return
        }
        if (state.isSaving) return
        val templates = selectedTemplates(state.selectedTemplateKeys)
        val customNames = state.customNames
        if (templates.isEmpty() && customNames.isEmpty()) {
            onComplete()
            return
        }
        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch(workerDispatcher) {
            val result = runCatching {
                val now = System.currentTimeMillis()
                templates.forEachIndexed { index, template ->
                    spaceRepository.insert(StarterSpaces.spaceFor(template, sortOrder = index, now = now))
                }
                customNames.forEachIndexed { index, name ->
                    spaceRepository.insert(
                        StarterSpaces.spaceFor(
                            template = CustomSpaceTemplate,
                            name = name,
                            sortOrder = templates.size + index,
                            now = now,
                        ),
                    )
                }
            }
            withContext(uiDispatcher) {
                result.onSuccess {
                    onComplete()
                }.onFailure {
                    _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                }
            }
        }
    }

    private fun selectedTemplates(selectedKeys: Set<String>): List<StarterSpaceTemplate> =
        StarterSpaces.templates.filter { it.key in selectedKeys }

    class Factory(
        private val isReplay: Boolean,
        private val container: OrbitContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TutorialSpaceSetupViewModel::class.java))
            return TutorialSpaceSetupViewModel(isReplay, container.spaceRepository) as T
        }
    }

    private companion object {
        val CustomSpaceTemplate = StarterSpaceTemplate(
            key = "custom",
            storedName = "",
            icon = "folder",
            colorAccent = "#E0A84F",
        )
    }
}

private fun cleanName(value: String): String = value.trim().replace(WhitespacePattern, " ")

private fun canonicalName(value: String): String = cleanName(value).lowercase(Locale.ROOT)

private val WhitespacePattern = Regex("\\s+")
