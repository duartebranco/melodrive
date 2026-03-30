package com.melodrive.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val YTDLP_RELEASE_URL =
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android"

object YtDlpInstaller {

    fun binaryFile(context: Context): File =
        File(context.filesDir, "yt-dlp")

    fun isInstalled(context: Context): Boolean =
        binaryFile(context).let { it.exists() && it.canExecute() }

    // downloads yt-dlp arm binary from official github releases on first launch
    suspend fun install(context: Context) = withContext(Dispatchers.IO) {
        val file = binaryFile(context)
        URL(YTDLP_RELEASE_URL).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.setExecutable(true)
    }
}
