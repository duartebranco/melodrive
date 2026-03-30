package com.melodrive.ui.screens

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodrive.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private var tickerJob: Job? = null

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
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                positionMs = currentPosition(state),
            )
            if (isPlaying) startTicker() else stopTicker()
        }
    }

    // ticks every 500ms while playing to keep the seek bar moving
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(500L)
                _state.value = _state.value.copy(
                    positionMs = currentPosition(controller?.playbackState),
                )
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // derives real-time position from the state snapshot + elapsed wall-clock time
    private fun currentPosition(state: PlaybackStateCompat?): Long {
        if (state == null) return 0L
        if (state.state != PlaybackStateCompat.STATE_PLAYING) return state.position
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return (state.position + elapsed * state.playbackSpeed).toLong()
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
        stopTicker()
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
