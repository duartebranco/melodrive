package com.melodrive.service

import android.content.Context
import android.net.Uri
import com.melodrive.library.LibraryScanner
import com.melodrive.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_NAME = "melodrive_prefs"
private const val KEY_FOLDER_URI = "folder_uri"

// in-process singleton; also persists folder uri across restarts
object MusicRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    fun setTracks(tracks: List<Track>) {
        _tracks.value = tracks
    }

    fun saveFolderUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun loadFolderUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    // called by MusicService on start if tracks are empty
    suspend fun loadFromStoredFolder(context: Context) {
        val uri = loadFolderUri(context) ?: return
        if (_tracks.value.isNotEmpty()) return
        val scanned = LibraryScanner.scanFolder(context, uri)
        _tracks.value = scanned
    }
}
