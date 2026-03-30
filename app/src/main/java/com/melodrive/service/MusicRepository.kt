package com.melodrive.service

import android.content.Context
import android.net.Uri
import com.melodrive.library.LibraryScanner
import com.melodrive.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_NAME = "melodrive_prefs"
private const val KEY_FOLDER_URI = "folder_uri"

object MusicRepository {

    // local files from the user's chosen folder — shown in Library and Android Auto
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks

    // the active playback queue — may be local tracks or a single YouTube track
    // kept separate so youtube playback never overwrites the library
    private val _playbackQueue = MutableStateFlow<List<Track>>(emptyList())
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue

    fun setLocalTracks(tracks: List<Track>) {
        _localTracks.value = tracks
        // keep queue in sync when replaying from library
        if (_playbackQueue.value.all { t -> tracks.any { it.id == t.id } }) {
            _playbackQueue.value = tracks
        }
    }

    fun setPlaybackQueue(tracks: List<Track>) {
        _playbackQueue.value = tracks
    }

    // looks up a track by id — playback queue first (youtube), then local library
    fun findById(id: String): Track? =
        _playbackQueue.value.find { it.id == id }
            ?: _localTracks.value.find { it.id == id }

    fun saveFolderUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun loadFolderUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    suspend fun loadFromStoredFolder(context: Context) {
        val uri = loadFolderUri(context) ?: return
        if (_localTracks.value.isNotEmpty()) return
        val scanned = LibraryScanner.scanFolder(context, uri)
        _localTracks.value = scanned
        _playbackQueue.value = scanned
    }
}
