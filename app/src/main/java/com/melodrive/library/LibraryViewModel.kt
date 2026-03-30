package com.melodrive.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodrive.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val folderUri: Uri? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    fun setFolder(uri: Uri) {
        _state.value = _state.value.copy(folderUri = uri, loading = true)
        viewModelScope.launch {
            val tracks = LibraryScanner.scanFolder(getApplication(), uri)
            _state.value = _state.value.copy(tracks = tracks, loading = false)
        }
    }
}
