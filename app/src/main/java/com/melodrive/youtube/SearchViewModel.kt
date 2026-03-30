package com.melodrive.youtube

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val results: List<YtSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private var searchJob: Job? = null

    init {
        // populate the tab immediately so the user sees music without having to search first
        loadInitial()
    }

    private fun loadInitial() {
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val results = YtDlpWrapper.search("trending music")
            _state.value = _state.value.copy(results = results, loading = false)
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val results = YtDlpWrapper.search(q)
            _state.value = _state.value.copy(
                results = results,
                loading = false,
                error = if (results.isEmpty()) "no results" else null,
            )
        }
    }
}
