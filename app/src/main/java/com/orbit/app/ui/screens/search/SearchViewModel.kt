package com.orbit.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.search.LocalSearch
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.ui.navigation.ItemDetailType
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
    val filter: SearchFilter = SearchFilter.All,
    val spaces: List<SearchSpace> = emptyList(),
)

/** The chips under the search bar. Archived shows only archived items. */
enum class SearchFilter { All, Tasks, Reminders, Notes, Archived }

data class SearchSpace(val id: Long, val name: String, val icon: String, val colorAccent: String)

/** Which results a filter chip keeps. Thoughts still to sort show under All only. */
internal fun LocalSearchResult.matches(filter: SearchFilter): Boolean = when (filter) {
    SearchFilter.All -> status != LocalSearchStatus.Archived
    SearchFilter.Tasks -> type == ItemDetailType.Task && status != LocalSearchStatus.Archived
    SearchFilter.Reminders -> type == ItemDetailType.Reminder && status != LocalSearchStatus.Archived
    SearchFilter.Notes -> type == ItemDetailType.Note && status != LocalSearchStatus.Archived
    SearchFilter.Archived -> status == LocalSearchStatus.Archived
}

class SearchViewModel(
    container: OrbitContainer,
    private val localSearch: LocalSearch = LocalSearch(),
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val includeArchived = MutableStateFlow(false)
    private val filter = MutableStateFlow(SearchFilter.All)
    private val spaces = container.spaceRepository.observeAll()
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
    val uiState = combine(query, includeArchived, results, filter, spaces) { currentQuery, showArchived, found, chosen, allSpaces ->
        SearchUiState(
            query = currentQuery,
            includeArchived = showArchived,
            results = if (currentQuery.isBlank()) emptyList() else found.filter { it.matches(chosen) },
            filter = chosen,
            spaces = allSpaces
                .filterNot { it.hidden || it.archived }
                .sortedBy { it.sortOrder }
                .map { SearchSpace(it.id, it.name, it.icon, it.colorAccent) },
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

    fun setFilter(value: SearchFilter) {
        filter.value = value
        includeArchived.value = value == SearchFilter.Archived
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
