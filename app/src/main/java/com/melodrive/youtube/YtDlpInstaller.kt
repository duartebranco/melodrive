package com.melodrive.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val YTDLP_RELEASE_URL =
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android"

object YtDlpInstaller {

    fun binaryFile(context: Context): File =
        File(context.filesDir, "yt-dlp")

    fun isInstalled(context: Context): Boolean =
        binaryFile(context).let { it.exists() && it.canExecute() }

    suspend fun install(context: Context) = withContext(Dispatchers.IO) {
        val file = binaryFile(context)
        openStreamFollowingRedirects(YTDLP_RELEASE_URL).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.setExecutable(true)
    }

    // java's HttpURLConnection won't follow redirects that cross domains (e.g. github.com →
    // objects.githubusercontent.com), so we handle them manually up to a safe limit
    private fun openStreamFollowingRedirects(urlString: String, depth: Int = 0): InputStream {
        if (depth > 8) error("too many redirects from $urlString")
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "MeloDrive/1.0")
        }
        conn.connect()
        return when (val code = conn.responseCode) {
            HttpURLConnection.HTTP_OK -> conn.inputStream
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            307, 308 -> {
                val location = conn.getHeaderField("Location")
                    ?: error("redirect $code with no Location header")
                conn.disconnect()
                openStreamFollowingRedirects(location, depth + 1)
            }
            else -> error("unexpected HTTP $code from $urlString")
        }
    }
}
