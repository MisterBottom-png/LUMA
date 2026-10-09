package com.orbit.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.search.LocalSearch
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.SearchCorpus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val includeArchived: Boolean = false,
    val results: List<LocalSearchResult> = emptyList(),
)

class SearchViewModel(
    container: OrbitContainer,
    private val localSearch: LocalSearch = LocalSearch(),
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val includeArchived = MutableStateFlow(false)
    private val corpus = combine(
        container.captureRepository.observeAll(),
        container.noteRepository.observeAll(),
        container.taskRepository.observeAll(),
        container.reminderRepository.observeAll(),
        container.spaceRepository.observeAll(),
    ) { captures, notes, tasks, reminders, spaces ->
        SearchCorpus(
            captures = captures,
            notes = notes,
            tasks = tasks,
            reminders = reminders,
            spaces = spaces,
        )
    }

    /** Results follow typing after a short pause and are computed off the main thread. */
    @OptIn(FlowPreview::class)
    private val results = combine(query.debounce(SearchDebounceMillis), includeArchived, corpus) {
            currentQuery, showArchived, data ->
        localSearch.search(currentQuery, data, showArchived)
    }.flowOn(searchDispatcher)

    // The typed query is shown immediately; only the results wait for the debounce.
    val uiState = combine(query, includeArchived, results) { currentQuery, showArchived, found ->
        SearchUiState(
            query = currentQuery,
            includeArchived = showArchived,
            results = if (currentQuery.isBlank()) emptyList() else found,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun setIncludeArchived(value: Boolean) {
        includeArchived.value = value
    }

    private companion object {
        const val SearchDebounceMillis = 150L
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java))
            return SearchViewModel(container) as T
        }
    }
}
