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
    val ytDlpReady: Boolean = false,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private var searchJob: Job? = null

    init {
        ensureYtDlp()
    }

    private fun ensureYtDlp() {
        viewModelScope.launch {
            if (!YtDlpInstaller.isInstalled(getApplication())) {
                try {
                    YtDlpInstaller.install(getApplication())
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = "yt-dlp download failed: ${e.message}")
                    return@launch
                }
            }
            _state.value = _state.value.copy(ytDlpReady = true)
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
            try {
                val results = YtDlpWrapper.search(getApplication(), q)
                _state.value = _state.value.copy(results = results, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }
}
