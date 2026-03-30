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
import androidx.media3.exoplayer.ExoPlayer
import com.melodrive.library.ArtworkLoader
import com.melodrive.model.Track
import com.melodrive.model.TrackSource
import com.melodrive.youtube.YtDlpWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URL

class MusicService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MediaNotificationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // tracks currently loaded into the player queue
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
    }

    override fun onStartCommand(
        intent: android.content.Intent?,
        flags: Int,
        startId: Int,
    ): Int {
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
                LOCAL_ROOT_ID -> buildTrackItems(MusicRepository.tracks.value)
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

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val tracks = MusicRepository.tracks.value
            val index = tracks.indexOfFirst { it.id == mediaId }
            if (index < 0) return
            playQueue(tracks, index)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val q = query?.lowercase() ?: return
            val tracks = MusicRepository.tracks.value
            val index = tracks.indexOfFirst {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
            if (index >= 0) playQueue(tracks, index)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        serviceScope.launch {
            val items = tracks.mapIndexed { i, track ->
                if (track.source == TrackSource.YOUTUBE && i == startIndex) {
                    val streamUri = YtDlpWrapper.resolveStreamUrl(track.id) ?: track.uri
                    MediaItem.fromUri(streamUri)
                } else {
                    MediaItem.fromUri(track.uri)
                }
            }
            player.setMediaItems(items, startIndex, 0L)
            player.prepare()
            player.play()
            updateMetadata(tracks[startIndex])
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                val notification = notificationManager.buildNotification(mediaSession)
                startForeground(NOTIFICATION_ID, notification)
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationManager.update(mediaSession)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player.currentMediaItemIndex
            queue.getOrNull(index)?.let { updateMetadata(it) }
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
            val art = loadArtworkBitmap(track)
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                .apply {
                    if (art != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                }
                .build()
            mediaSession.setMetadata(metadata)
            notificationManager.update(mediaSession)
        }
    }

    private fun loadArtworkBitmap(track: Track): Bitmap? {
        return try {
            when {
                track.source == TrackSource.LOCAL ->
                    ArtworkLoader.loadEmbedded(this, track.uri)
                track.artworkUri != null -> {
                    URL(track.artworkUri.toString()).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
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
