package com.melodrive.youtube

import android.net.Uri

data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

// stub — implemented in pr 3
object YtDlpWrapper {

    suspend fun search(query: String): List<YtSearchResult> = emptyList()

    suspend fun resolveStreamUrl(videoId: String): Uri? = null
}
