package com.melodrive.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.InfoItem

data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

object YtDlpWrapper {

    suspend fun search(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val handler = YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                    "",
                )
                val info = SearchInfo.getInfo(YouTube, handler)
                info.relatedItems
                    .take(maxResults)
                    .mapNotNull { item ->
                        val stream = item as? org.schabi.newpipe.extractor.stream.StreamInfoItem
                            ?: return@mapNotNull null
                        YtSearchResult(
                            videoId = extractVideoId(stream.url) ?: return@mapNotNull null,
                            title = stream.name,
                            artist = stream.uploaderName ?: "",
                            thumbnailUrl = stream.thumbnails.firstOrNull()?.url ?: "",
                            durationSeconds = stream.duration.toInt(),
                        )
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun resolveStreamUrl(videoId: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(
                    YouTube,
                    "https://www.youtube.com/watch?v=$videoId",
                )
                val best = info.audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.averageBitrate }
                    ?: info.audioStreams.firstOrNull()
                best?.content?.let { Uri.parse(it) }
            } catch (_: Exception) {
                null
            }
        }

    private fun extractVideoId(url: String): String? =
        Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([^?&]+)").find(url)?.groupValues?.get(1)
}
