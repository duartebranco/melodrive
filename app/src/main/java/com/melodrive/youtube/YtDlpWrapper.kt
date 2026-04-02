package com.melodrive.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
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

    // Searches YouTube Music for songs, albums, and artists in parallel.
    // NewPipeExtractor only honours the first content filter per query, so we
    // run three separate searches and interleave the results.
    suspend fun search(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val songsJob = async {
                    searchByFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, ResultType.SONG)
                }
                val albumsJob = async {
                    searchByFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, ResultType.ALBUM)
                }
                val artistsJob = async {
                    searchByFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, ResultType.ARTIST)
                }

                val songs = songsJob.await()
                val albums = albumsJob.await()
                val artists = artistsJob.await()

                // Round-robin interleave: song, album, artist, song, album, artist…
                // Surfaces the top-ranked result from each type before any lower-ranked
                // ones, matching YouTube Music's natural mixed relevance order.
                buildList {
                    val songQ = ArrayDeque(songs)
                    val albumQ = ArrayDeque(albums)
                    val artistQ = ArrayDeque(artists)
                    while (size < maxResults) {
                        val s = songQ.removeFirstOrNull()
                        val a = albumQ.removeFirstOrNull()
                        val r = artistQ.removeFirstOrNull()
                        if (s == null && a == null && r == null) break
                        s?.let { add(it) }
                        a?.let { add(it) }
                        r?.let { add(it) }
                    }
                }
            }
        }

    // Searches for songs only — used as a fallback inside getArtistSongs().
    suspend fun searchSongs(query: String, maxResults: Int = 20): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            searchByFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, ResultType.SONG)
                .take(maxResults)
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

    // Browses the artist's own channel tabs (TRACKS first, then VIDEOS) so we
    // get their actual library instead of a generic name search.
    suspend fun getArtistSongs(url: String): List<YtSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val info = ChannelInfo.getInfo(YouTube, url)

                // Prefer the dedicated tracks/songs tab; fall back to videos.
                val tab = info.tabs.firstOrNull { tab ->
                    tab.contentFilters.contains(ChannelTabs.TRACKS)
                } ?: info.tabs.firstOrNull { tab ->
                    tab.contentFilters.contains(ChannelTabs.VIDEOS)
                }

                if (tab != null) {
                    val tracks = ChannelTabInfo.getInfo(YouTube, tab)
                        .relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { it.toResult(ResultType.SONG) }
                        .take(50)
                    if (tracks.isNotEmpty()) return@withContext tracks
                }

                // Fallback: name search filtered strictly to this artist so that songs
                // which merely mention the artist name in their title are excluded.
                searchByFilter(info.name, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, ResultType.SONG)
                    .filter { it.artist.lowercase().contains(info.name.lowercase()) }
                    .take(50)
            } catch (_: Exception) {
                emptyList()
            }
        }

    // Resolves a video ID to a direct audio stream URL.
    suspend fun resolveStreamUrl(videoId: String): Uri? =
        withContext(Dispatchers.IO) {
            val urls = listOf(
                "https://music.youtube.com/watch?v=$videoId",
                "https://www.youtube.com/watch?v=$videoId",
            )
            for (url in urls) {
                try {
                    val streams = StreamInfo.getInfo(YouTube, url).audioStreams
                    val best = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        .maxByOrNull { it.averageBitrate }
                        ?: streams.firstOrNull()
                    if (best?.content != null) return@withContext Uri.parse(best.content)
                } catch (_: Exception) {
                    // try next url
                }
            }
            null
        }

    // Runs a single-type YouTube Music search on the calling (IO) thread.
    private fun searchByFilter(query: String, filter: String, type: ResultType): List<YtSearchResult> =
        try {
            val handler = YouTube.searchQHFactory.fromQuery(query, listOf(filter), "")
            SearchInfo.getInfo(YouTube, handler).relatedItems.mapNotNull { item ->
                when (type) {
                    ResultType.SONG -> (item as? StreamInfoItem)?.toResult(ResultType.SONG)
                    ResultType.ALBUM -> (item as? org.schabi.newpipe.extractor.playlist.PlaylistInfoItem)?.let {
                        YtSearchResult(
                            videoId = it.url,
                            title = it.name,
                            artist = it.uploaderName ?: "",
                            thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: "",
                            durationSeconds = 0,
                            type = ResultType.ALBUM,
                        )
                    }
                    ResultType.ARTIST -> (item as? org.schabi.newpipe.extractor.channel.ChannelInfoItem)?.let {
                        YtSearchResult(
                            videoId = it.url,
                            title = it.name,
                            artist = "",
                            thumbnailUrl = it.thumbnails.firstOrNull()?.url ?: "",
                            durationSeconds = 0,
                            type = ResultType.ARTIST,
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
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
