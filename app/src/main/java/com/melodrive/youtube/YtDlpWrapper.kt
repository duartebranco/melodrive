package com.melodrive.youtube

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

object YtDlpWrapper {

    // search youtube for `query`, return up to `maxResults` items
    suspend fun search(
        context: Context,
        query: String,
        maxResults: Int = 20,
    ): List<YtSearchResult> = withContext(Dispatchers.IO) {
        val bin = YtDlpInstaller.binaryFile(context)
        val output = runYtDlp(
            bin,
            "--no-warnings",
            "--print", "%(id)s\t%(title)s\t%(uploader)s\t%(thumbnail)s\t%(duration)s",
            "--flat-playlist",
            "ytsearch${maxResults}:${query}",
        )
        output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 5) return@mapNotNull null
                YtSearchResult(
                    videoId = parts[0],
                    title = parts[1],
                    artist = parts[2],
                    thumbnailUrl = parts[3],
                    durationSeconds = parts[4].toIntOrNull() ?: 0,
                )
            }
    }

    // resolve a youtube video id to a direct audio stream url
    suspend fun resolveStreamUrl(context: Context, videoId: String): Uri? =
        withContext(Dispatchers.IO) {
            val bin = YtDlpInstaller.binaryFile(context)
            try {
                val json = runYtDlp(
                    bin,
                    "--no-warnings",
                    "-j",
                    "--format", "bestaudio[ext=webm]/bestaudio/best",
                    "https://www.youtube.com/watch?v=$videoId",
                )
                val url = JSONObject(json).optString("url").takeIf { it.isNotBlank() }
                url?.let { Uri.parse(it) }
            } catch (_: Exception) {
                null
            }
        }

    private fun runYtDlp(bin: File, vararg args: String): String {
        val process = ProcessBuilder(bin.absolutePath, *args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }
}
