package com.melodrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.melodrive.R

const val NOTIFICATION_ID = 1001
const val CHANNEL_ID = "melodrive_playback"

class MediaNotificationManager(private val service: MusicService) {

    private val notificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.music_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    fun buildNotification(session: MediaSessionCompat): Notification {
        val meta = session.controller.metadata
        val state = session.controller.playbackState
        val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING

        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            service, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            service, PlaybackStateCompat.ACTION_PLAY_PAUSE
        )
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            service, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )

        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(meta?.description?.title ?: "MeloDrive")
            .setContentText(meta?.description?.subtitle ?: "")
            .setLargeIcon(meta?.description?.iconBitmap)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "previous", prevIntent
            ))
            .addAction(NotificationCompat.Action(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (isPlaying) "pause" else "play",
                playPauseIntent,
            ))
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_next, "next", nextIntent
            ))
            .build()
    }

    fun update(session: MediaSessionCompat) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(session))
    }
}
