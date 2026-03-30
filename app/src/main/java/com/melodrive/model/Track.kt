package com.melodrive.model

import android.net.Uri

enum class TrackSource { LOCAL, YOUTUBE }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val source: TrackSource,
    // local: file uri; youtube: video id (resolved to stream url at play time)
    val uri: Uri,
    val artworkUri: Uri? = null,
)
