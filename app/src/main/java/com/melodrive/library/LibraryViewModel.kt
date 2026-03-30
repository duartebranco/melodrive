package com.melodrive.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodrive.model.Track
import com.melodrive.service.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Album(
    val name: String,
    val artist: String,
    val tracks: List<Track>,
    val artworkUri: Uri? = null,
)

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val loading: Boolean = false,
    val folderUri: Uri? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        val savedUri = MusicRepository.loadFolderUri(app)
        val cached = MusicRepository.localTracks.value
        if (savedUri != null && cached.isNotEmpty()) {
            _state.value = LibraryState(
                tracks = cached,
                albums = albumsFrom(cached),
                folderUri = savedUri,
            )
        } else if (savedUri != null) {
            setFolder(savedUri)
        }
    }

    fun setFolder(uri: Uri) {
        _state.value = _state.value.copy(folderUri = uri, loading = true)
        viewModelScope.launch {
            val tracks = LibraryScanner.scanFolder(getApplication(), uri)
            MusicRepository.setLocalTracks(tracks)
            MusicRepository.saveFolderUri(getApplication(), uri)
            _state.value = _state.value.copy(
                tracks = tracks,
                albums = albumsFrom(tracks),
                loading = false,
            )
        }
    }

    private fun albumsFrom(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.album.ifEmpty { "Unknown Album" } }
            .map { (name, albumTracks) ->
                Album(
                    name = name,
                    artist = albumTracks.firstOrNull()?.artist ?: "",
                    tracks = albumTracks,
                    artworkUri = albumTracks.firstOrNull()?.artworkUri,
                )
            }
            .sortedBy { it.name }
}
