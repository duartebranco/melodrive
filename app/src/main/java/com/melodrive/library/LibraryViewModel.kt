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

data class Artist(
    val name: String,
    val tracks: List<Track>,
    val artworkUri: Uri? = null,
)

data class Folder(
    val name: String,
    val tracks: List<Track>,
)

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
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
                artists = artistsFrom(cached),
                folders = foldersFrom(cached),
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
                artists = artistsFrom(tracks),
                folders = foldersFrom(tracks),
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

    private fun artistsFrom(tracks: List<Track>): List<Artist> =
        tracks
            .groupBy { it.artist.ifEmpty { "Unknown Artist" } }
            .map { (name, artistTracks) ->
                Artist(
                    name = name,
                    tracks = artistTracks,
                    artworkUri = artistTracks.firstOrNull()?.artworkUri,
                )
            }
            .sortedBy { it.name }

    private fun foldersFrom(tracks: List<Track>): List<Folder> =
        tracks
            .groupBy { it.folder.ifEmpty { "Unknown Folder" } }
            .map { (name, folderTracks) ->
                Folder(
                    name = name,
                    tracks = folderTracks,
                )
            }
            .sortedBy { it.name }
}
