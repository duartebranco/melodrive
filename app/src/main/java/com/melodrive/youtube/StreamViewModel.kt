package com.melodrive.youtube

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StreamState(
    val query: String = "",
    val results: List<YtSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class StreamViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("stream_history", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state

    private var searchJob: Job? = null

    init {
        // populate the tab immediately so the user sees music without having to search first
        loadInitial()
    }

    fun addToHistory(results: List<YtSearchResult>) {
        val currentHistory = prefs.getString("history", "") ?: ""
        val historyList = currentHistory.split("|||").filter { it.isNotEmpty() }.toMutableList()
        for (result in results) {
            val str =
                "${result.videoId}||${result.title}||${result.artist}||${result.thumbnailUrl}||${result.durationSeconds}||${result.type.name}"
            historyList.remove(str)
            historyList.add(0, str)
        }
        prefs.edit().putString("history", historyList.take(20).joinToString("|||")).apply()
    }

    private fun loadInitial() {
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val historyStr = prefs.getString("history", "") ?: ""
            if (historyStr.isNotEmpty()) {
                val results = historyStr.split("|||").mapNotNull {
                    val p = it.split("||")
                    if (p.size >= 5) {
                        YtSearchResult(
                            p[0],
                            p[1],
                            p[2],
                            p[3],
                            p[4].toIntOrNull() ?: 0,
                            ResultType.valueOf(p.getOrElse(5) { "SONG" })
                        )
                    } else null
                }
                _state.value = _state.value.copy(results = results, loading = false)
            } else {
                _state.value = _state.value.copy(results = emptyList(), loading = false)
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        if (q.isEmpty()) {
            searchJob?.cancel()
            loadInitial()
        }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            searchJob?.cancel()
            loadInitial()
            return
        }
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
