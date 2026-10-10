package com.orbit.app.ui

import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.domain.model.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Coalesces rapid settings changes (slider drags, typing a name) into one ordered
 * write. The newest choice shows immediately through [overlay]; the store is written
 * once things settle, so writes can never land out of order.
 */
@OptIn(FlowPreview::class)
class DebouncedSettingsWriter(
    scope: CoroutineScope,
    private val repository: AppSettingsRepository,
    debounceMillis: Long = DefaultDebounceMillis,
) {
    private val pending = MutableStateFlow<AppSettings?>(null)

    /** The newest unsaved settings, or null when the store is up to date. */
    val overlay: StateFlow<AppSettings?> = pending.asStateFlow()

    init {
        scope.launch {
            pending
                .filterNotNull()
                .debounce(debounceMillis)
                .collect { target ->
                    try {
                        repository.update(target)
                        pending.compareAndSet(target, null)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // Show what is really stored rather than a choice that was not saved.
                        pending.compareAndSet(target, null)
                    }
                }
        }
    }

    fun submit(settings: AppSettings) {
        pending.value = settings
    }

    /** Drops an unsaved change, e.g. before resetting everything. */
    fun discardPending() {
        pending.value = null
    }

    companion object {
        const val DefaultDebounceMillis = 250L
    }
}
