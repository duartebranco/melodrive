package com.melodrive.ui.screens

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodrive.model.Track
import com.melodrive.service.MusicRepository
import com.melodrive.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val connected: Boolean = false,
    val repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE,
)

class NowPlayingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    val history: StateFlow<List<Track>> = MusicRepository.history
    val mainBuffer: StateFlow<List<Track>> = MusicRepository.mainBuffer

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

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat?) {
            val isPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                positionMs = currentPosition(playbackState),
            )
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(repeatMode = repeatMode)
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
        stopTicker()
        controller?.unregisterCallback(controllerCallback)
        mediaBrowser?.disconnect()
        mediaBrowser = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    fun clearHistory() {
        MusicRepository.clearHistory()
    }

    fun clearMainBuffer() {
        MusicRepository.clearMainBuffer()
    }

    fun removeFromMainBuffer(trackId: String) {
        val current = mainBuffer.value
        val removeIndex = current.indexOfFirst { it.id == trackId }
        if (removeIndex < 0) return

        val isCurrentTrack = state.value.connected && state.value.title.isNotEmpty() &&
                current.getOrNull(removeIndex)?.title == state.value.title

        val updated = MusicRepository.removeFromMainBuffer(trackId)

        if (updated.isEmpty()) {
            controller?.transportControls?.pause()
            return
        }

        if (isCurrentTrack) {
            val nextIndex = removeIndex.coerceAtMost(updated.lastIndex)
            playFromMainBufferIndex(nextIndex)
        }
    }

    fun playFromMainBuffer(track: Track) {
        MusicRepository.addToMainBufferAndMoveToFront(track)
        controller?.transportControls?.playFromMediaId(track.id, null)
    }

    fun playFromMainBufferIndex(index: Int) {
        val buffer = mainBuffer.value
        if (index !in buffer.indices) return
        val track = buffer[index]
        MusicRepository.addToMainBufferAndMoveToFront(track)
        controller?.transportControls?.playFromMediaId(track.id, null)
    }

    fun togglePlayPause() {
        controller?.transportControls?.let {
            if (_state.value.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() {
        controller?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    fun toggleRepeatMode() {
        val nextMode = when (_state.value.repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
            PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
        controller?.transportControls?.setRepeatMode(nextMode)
    }

    private fun syncFromController(c: MediaControllerCompat) {
        controllerCallback.onMetadataChanged(c.metadata)
        controllerCallback.onPlaybackStateChanged(c.playbackState)
        controllerCallback.onRepeatModeChanged(c.repeatMode)
    }

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

    private fun currentPosition(playbackState: PlaybackStateCompat?): Long {
        if (playbackState == null) return 0L
        if (playbackState.state != PlaybackStateCompat.STATE_PLAYING) return playbackState.position
        val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
        return (playbackState.position + elapsed * playbackState.playbackSpeed).toLong()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
