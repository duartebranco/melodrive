package com.melodrive.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

enum class ResultType {
    SONG, ALBUM, ARTIST
}

data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val type: ResultType = ResultType.SONG,
)

object YtDlpWrapper {

    // searches YouTube Music for songs, albums, and artists
    suspend fun search(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val handler = YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf(
                        YoutubeSearchQueryHandlerFactory.MUSIC_SONGS,
                        YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS,
                        YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS,
                    ),
                    "",
                )
                SearchInfo.getInfo(YouTube, handler).relatedItems
                    .take(maxResults)
                    .mapNotNull { item ->
                        when (item) {
                            is StreamInfoItem -> item.toResult(ResultType.SONG)
                            is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> YtSearchResult(
                                videoId = item.url,
                                title = item.name,
                                artist = item.uploaderName ?: "",
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                durationSeconds = 0,
                                type = ResultType.ALBUM,
                            )
                            is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> YtSearchResult(
                                videoId = item.url,
                                title = item.name,
                                artist = "",
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                durationSeconds = 0,
                                type = ResultType.ARTIST,
                            )
                            else -> null
                        }
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // searches YouTube Music for songs only
    suspend fun searchSongs(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val handler = YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                    "",
                )
                SearchInfo.getInfo(YouTube, handler).relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(maxResults)
                    .mapNotNull { it.toResult(ResultType.SONG) }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun getAlbumSongs(url: String): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(YouTube, url)
                    .relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.toResult(ResultType.SONG) }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun getArtistSongs(url: String): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(YouTube, url)
                searchSongs(info.name, 50)
            } catch (_: Exception) {
                emptyList()
            }
        }

    // resolves a video id to a direct audio stream url
    suspend fun resolveStreamUrl(videoId: String): Uri? =
        withContext(Dispatchers.IO) {
            val urls = listOf(
                "https://music.youtube.com/watch?v=$videoId",
                "https://www.youtube.com/watch?v=$videoId",
            )
            for (url in urls) {
                try {
                    val best = StreamInfo.getInfo(YouTube, url).audioStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        .maxByOrNull { it.averageBitrate }
                        ?: StreamInfo.getInfo(YouTube, url).audioStreams.firstOrNull()
                    if (best?.content != null) return@withContext Uri.parse(best.content)
                } catch (_: Exception) {
                    // try next url
                }
            }
            null
        }

    private fun StreamInfoItem.toResult(type: ResultType): YtSearchResult? {
        val id = extractVideoId(url) ?: return null
        return YtSearchResult(
            videoId = id,
            title = name,
            artist = uploaderName ?: "",
            thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
            durationSeconds = duration.toInt(),
            type = type,
        )
    }

    private fun extractVideoId(url: String): String? =
        Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([^?&]+)").find(url)?.groupValues?.get(1)
}
