package com.melodrive.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
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

    // searches YouTube Music (music.youtube.com) for songs — returns only music, no videos
    suspend fun search(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val handler = YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf(
                        YoutubeSearchQueryHandlerFactory.MUSIC_SONGS,
                        YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS,
                        YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
                    ),
                    "",
                )
                val info = SearchInfo.getInfo(YouTube, handler)
                info.relatedItems
                    .take(maxResults)
                    .mapNotNull { item ->
                        when (item) {
                            is StreamInfoItem -> YtSearchResult(
                                videoId = extractVideoId(item.url) ?: return@mapNotNull null,
                                title = item.name,
                                artist = item.uploaderName ?: "",
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                durationSeconds = item.duration.toInt(),
                                type = ResultType.SONG
                            )

                            is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> YtSearchResult(
                                videoId = item.url, // For albums, videoId is the url for later fetching
                                title = item.name,
                                artist = item.uploaderName ?: "",
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                durationSeconds = 0,
                                type = ResultType.ALBUM
                            )

                            is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> YtSearchResult(
                                videoId = item.url, // For artists, videoId is the channel url
                                title = item.name,
                                artist = "",
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                durationSeconds = 0,
                                type = ResultType.ARTIST
                            )

                            else -> null
                        }
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun searchSongs(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val handler = YouTube.searchQHFactory.fromQuery(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                    "",
                )
                val info = SearchInfo.getInfo(YouTube, handler)
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(maxResults)
                    .mapNotNull { item ->
                        YtSearchResult(
                            videoId = extractVideoId(item.url) ?: return@mapNotNull null,
                            title = item.name,
                            artist = item.uploaderName ?: "",
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            durationSeconds = item.duration.toInt(),
                            type = ResultType.SONG
                        )
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun getAlbumSongs(url: String): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(YouTube, url)
                info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    YtSearchResult(
                        videoId = extractVideoId(stream.url) ?: return@mapNotNull null,
                        title = stream.name,
                        artist = stream.uploaderName ?: "",
                        thumbnailUrl = stream.thumbnails.firstOrNull()?.url ?: "",
                        durationSeconds = stream.duration.toInt(),
                        type = ResultType.SONG
                    )
                }
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

    // resolves a video id to a direct audio stream url via YouTube Music
    suspend fun resolveStreamUrl(videoId: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(
                    YouTube,
                    "https://music.youtube.com/watch?v=$videoId",
                )
                val best = info.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.averageBitrate }
                    ?: info.audioStreams.firstOrNull()
                best?.content?.let { Uri.parse(it) }
            } catch (_: Exception) {
                // fallback to regular youtube url
                try {
                    val info = StreamInfo.getInfo(
                        YouTube,
                        "https://www.youtube.com/watch?v=$videoId",
                    )
                    val best = info.audioStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        .maxByOrNull { it.averageBitrate }
                        ?: info.audioStreams.firstOrNull()
                    best?.content?.let { Uri.parse(it) }
                } catch (_: Exception) {
                    null
                }
            }
        }

    private fun extractVideoId(url: String): String? =
        Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([^?&]+)").find(url)?.groupValues?.get(1)
}
