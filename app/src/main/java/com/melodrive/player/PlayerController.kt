package com.melodrive.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.melodrive.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class PlayerController(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
    }

    fun play(track: Track) {
        val item = MediaItem.fromUri(track.uri)
        player.setMediaItem(item)
        player.prepare()
        player.play()
        _state.value = _state.value.copy(currentTrack = track)
    }

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        val items = tracks.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        _state.value = _state.value.copy(currentTrack = tracks.getOrNull(startIndex))
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() = player.seekToNextMediaItem()

    fun skipPrevious() = player.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun release() = player.release()
}
