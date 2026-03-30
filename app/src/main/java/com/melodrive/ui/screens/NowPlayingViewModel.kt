package com.melodrive.ui.screens

import android.app.Application
import android.content.ComponentName
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import com.melodrive.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val artworkUri: android.net.Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val connected: Boolean = false,
)

class NowPlayingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state

    private var mediaBrowser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = mediaBrowser ?: return
            controller = MediaControllerCompat(getApplication(), browser.sessionToken).also {
                it.registerCallback(controllerCallback)
                syncFromController(it)
            }
            _state.value = _state.value.copy(connected = true)
        }

        override fun onConnectionSuspended() {
            _state.value = _state.value.copy(connected = false)
        }

        override fun onConnectionFailed() {
            _state.value = _state.value.copy(connected = false)
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata ?: return
            _state.value = _state.value.copy(
                title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "",
                artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "",
                artworkUri = metadata.description.iconUri,
                durationMs = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
            )
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _state.value = _state.value.copy(
                isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING,
                positionMs = state?.position ?: 0L,
            )
        }
    }

    fun connect() {
        if (mediaBrowser?.isConnected == true) return
        mediaBrowser = MediaBrowserCompat(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java),
            connectionCallback,
            null,
        ).also { it.connect() }
    }

    fun disconnect() {
        controller?.unregisterCallback(controllerCallback)
        mediaBrowser?.disconnect()
        mediaBrowser = null
        controller = null
    }

    fun togglePlayPause() = controller?.transportControls?.let {
        if (_state.value.isPlaying) it.pause() else it.play()
    }

    fun skipNext() = controller?.transportControls?.skipToNext()

    fun skipPrevious() = controller?.transportControls?.skipToPrevious()

    fun seekTo(positionMs: Long) = controller?.transportControls?.seekTo(positionMs)

    private fun syncFromController(c: MediaControllerCompat) {
        controllerCallback.onMetadataChanged(c.metadata)
        controllerCallback.onPlaybackStateChanged(c.playbackState)
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
