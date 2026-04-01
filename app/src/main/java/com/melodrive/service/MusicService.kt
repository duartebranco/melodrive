package com.melodrive.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.exoplayer.ExoPlayer
import com.melodrive.library.ArtworkLoader
import com.melodrive.model.Track
import com.melodrive.model.TrackSource
import com.melodrive.youtube.YtDlpWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

class MusicService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MediaNotificationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var queue: List<Track> = emptyList()

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        notificationManager = MediaNotificationManager(this)

        serviceScope.launch { MusicRepository.loadFromStoredFolder(this@MusicService) }

        serviceScope.launch {
            MusicRepository.mainBuffer.collect { buffer ->
                if (buffer.isEmpty()) {
                    queue = emptyList()
                    player.clearMediaItems()
                    player.stop()
                } else if (queue.isNotEmpty()) {
                    val bufferIds = buffer.map { it.id }.toSet()
                    val toRemoveIndices = mutableListOf<Int>()
                    for (i in queue.indices.reversed()) {
                        if (queue[i].id !in bufferIds) {
                            toRemoveIndices.add(i)
                        }
                    }
                    if (toRemoveIndices.isNotEmpty()) {
                        val newQueue = queue.toMutableList()
                        toRemoveIndices.forEach { index ->
                            if (index < player.mediaItemCount) {
                                player.removeMediaItem(index)
                            }
                            newQueue.removeAt(index)
                        }
                        queue = newQueue
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot = BrowserRoot(MEDIA_ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        serviceScope.launch {
            val items = when (parentId) {
                MEDIA_ROOT_ID -> buildRootItems()
                LOCAL_ROOT_ID -> buildTrackItems(MusicRepository.localTracks.value)
                else -> emptyList()
            }
            result.sendResult(items)
        }
    }

    private fun buildRootItems() = listOf(
        browsableItem(LOCAL_ROOT_ID, "Local Music", "your library"),
    )

    private fun buildTrackItems(tracks: List<Track>) = tracks.map { track ->
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(track.id)
            .setTitle(track.title)
            .setSubtitle(track.artist)
            .apply { track.artworkUri?.let { setIconUri(it) } }
            .build()
        MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun browsableItem(id: String, title: String, subtitle: String) =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = player.play()
        override fun onPause() = player.pause()
        override fun onStop() {
            player.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            mediaSession.isActive = false
        }

        override fun onSkipToNext() = player.seekToNextMediaItem()
        override fun onSkipToPrevious() = player.seekToPreviousMediaItem()
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
        override fun onSetRepeatMode(repeatMode: Int) {
            player.repeatMode = when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> REPEAT_MODE_ONE
                PlaybackStateCompat.REPEAT_MODE_ALL -> REPEAT_MODE_ALL
                else -> REPEAT_MODE_OFF
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val trackId = mediaId ?: return
            val tracks = MusicRepository.mainBuffer.value
            if (tracks.isEmpty()) return
            val index = tracks.indexOfFirst { it.id == trackId }
            if (index < 0) return
            playQueue(tracks, index)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val q = query?.lowercase() ?: return
            val tracks = MusicRepository.localTracks.value
            val index = tracks.indexOfFirst {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
            if (index >= 0) playQueue(tracks, index)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        serviceScope.launch {
            // Resolve every YouTube track's stream URL in parallel before touching
            // ExoPlayer — raw watch?v= URLs are not playable by the player.
            val items = coroutineScope {
                tracks.map { track ->
                    async(Dispatchers.IO) {
                        if (track.source == TrackSource.YOUTUBE) {
                            val uri = YtDlpWrapper.resolveStreamUrl(track.id) ?: track.uri
                            MediaItem.fromUri(uri)
                        } else {
                            MediaItem.fromUri(track.uri)
                        }
                    }
                }.awaitAll()
            }
            player.setMediaItems(items, startIndex, 0L)
            player.prepare()
            player.play()
            val track = tracks[startIndex]
            updateMetadata(track)
            MusicRepository.addToHistory(track)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            mediaSession.setRepeatMode(
                when (repeatMode) {
                    REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                    REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startForeground(NOTIFICATION_ID, notificationManager.buildNotification(mediaSession))
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationManager.update(mediaSession)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackState()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            queue.getOrNull(player.currentMediaItemIndex)?.let { track ->
                updateMetadata(track)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    MusicRepository.addToHistory(track)
                }
            }
        }
    }

    private fun updatePlaybackState() {
        val state = when {
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, player.currentPosition, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .build()
        )
    }

    private fun updateMetadata(track: Track) {
        serviceScope.launch(Dispatchers.IO) {
            val artBitmap = loadArtworkBitmap(track)
            // uri used by both the notification large icon and the Now Playing screen's Coil image
            val artUri = when {
                track.source == TrackSource.YOUTUBE -> track.artworkUri?.toString()
                artBitmap != null -> saveArtworkToCache(track.id, artBitmap)
                else -> null
            }
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                .apply {
                    if (artBitmap != null) {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                    }
                    if (artUri != null) {
                        // DISPLAY_ICON_URI is what MediaDescriptionCompat.iconUri reads —
                        // this is what NowPlayingViewModel exposes to the UI as artworkUri
                        putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                        putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
                    }
                }
                .build()
            mediaSession.setMetadata(metadata)
            notificationManager.update(mediaSession)
        }
    }

    private fun loadArtworkBitmap(track: Track): Bitmap? = try {
        when (track.source) {
            TrackSource.LOCAL -> ArtworkLoader.loadEmbedded(this, track.uri)
            TrackSource.YOUTUBE -> track.artworkUri?.let { uri ->
                URL(uri.toString()).openStream().use { BitmapFactory.decodeStream(it) }
            }
        }
    } catch (_: Exception) {
        null
    }

    // saves a bitmap to cache and returns a file:// uri string coil can load
    private fun saveArtworkToCache(trackId: String, bitmap: Bitmap): String? = try {
        val safe = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(cacheDir, "art_$safe.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Uri.fromFile(file).toString()
    } catch (_: Exception) {
        null
    }

    override fun onDestroy() {
        serviceJob.cancel()
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MusicService"
        const val MEDIA_ROOT_ID = "melodrive_root"
        const val LOCAL_ROOT_ID = "melodrive_local"
    }
}
