package com.orbit.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.search.LocalSearch
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.SearchCorpus
import kotlinx.coroutines.flow.MutableStateFlow
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

    val uiState = combine(query, includeArchived, corpus) { currentQuery, showArchived, data ->
        SearchUiState(
            query = currentQuery,
            includeArchived = showArchived,
            results = localSearch.search(currentQuery, data, showArchived),
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

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java))
            return SearchViewModel(container) as T
        }
    }
}
