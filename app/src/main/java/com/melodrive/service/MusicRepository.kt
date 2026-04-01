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

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks

    private val _mainBuffer = MutableStateFlow<List<Track>>(emptyList())
    val mainBuffer: StateFlow<List<Track>> = _mainBuffer

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history

    fun setLocalTracks(tracks: List<Track>) {
        _localTracks.value = tracks
    }

    fun setMainBuffer(tracks: List<Track>) {
        _mainBuffer.value = tracks.distinctBy { it.id }
    }

    fun clearMainBuffer() {
        _mainBuffer.value = emptyList()
    }

    fun addToMainBuffer(track: Track): List<Track> {
        val next = _mainBuffer.value.toMutableList()
        if (next.none { it.id == track.id }) {
            next.add(track)
            _mainBuffer.value = next
        }
        return _mainBuffer.value
    }

    fun addAllToMainBuffer(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return _mainBuffer.value
        val next = _mainBuffer.value.toMutableList()
        val existingIds = next.map { it.id }.toHashSet()
        tracks.forEach { track ->
            if (existingIds.add(track.id)) next.add(track)
        }
        _mainBuffer.value = next
        return next
    }

    fun addToMainBufferAndMoveToFront(track: Track): List<Track> {
        val next = _mainBuffer.value.toMutableList()
        next.removeAll { it.id == track.id }
        next.add(0, track)
        _mainBuffer.value = next
        return next
    }

    fun removeFromMainBuffer(trackId: String): List<Track> {
        val next = _mainBuffer.value.filterNot { it.id == trackId }
        _mainBuffer.value = next
        return next
    }

    fun moveInMainBuffer(fromIndex: Int, toIndex: Int): List<Track> {
        val list = _mainBuffer.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
            return list
        }
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _mainBuffer.value = list
        return list
    }

    fun indexOfInMainBuffer(trackId: String): Int {
        return _mainBuffer.value.indexOfFirst { it.id == trackId }
    }

    fun findById(id: String): Track? {
        return _mainBuffer.value.find { it.id == id }
            ?: _localTracks.value.find { it.id == id }
    }

    fun addToHistory(track: Track) {
        val current = _history.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        _history.value = current.take(50)
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun saveFolderUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .apply()
    }

    fun loadFolderUri(context: Context): Uri? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
            ?.let(Uri::parse)
    }

    suspend fun loadFromStoredFolder(context: Context) {
        val uri = loadFolderUri(context) ?: return
        if (_localTracks.value.isNotEmpty()) return
        val scanned = LibraryScanner.scanFolder(context, uri)
        _localTracks.value = scanned
    }
}
