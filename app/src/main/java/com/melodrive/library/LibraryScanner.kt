package com.melodrive.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.melodrive.model.Track
import com.melodrive.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val SUPPORTED_MIME_TYPES = setOf(
    "audio/mpeg",       // mp3
    "audio/flac",       // flac
    "audio/ogg",        // ogg
    "audio/mp4",        // m4a
    "audio/x-m4a",
    "audio/aac",
    "audio/wav",
    "audio/x-wav",
)

object LibraryScanner {

    suspend fun scanFolder(context: Context, folderUri: Uri): List<Track> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
            buildList { collectAudioFiles(context, root, this) }
        }

    private fun collectAudioFiles(
        context: Context,
        dir: DocumentFile,
        out: MutableList<Track>,
    ) {
        for (file in dir.listFiles()) {
            when {
                file.isDirectory -> collectAudioFiles(context, file, out)
                file.type in SUPPORTED_MIME_TYPES -> {
                    val track = readTrack(context, file, dir.name ?: "") ?: continue
                    out.add(track)
                }
            }
        }
    }

    private fun readTrack(context: Context, file: DocumentFile, folderName: String): Track? {
        val uri = file.uri
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.name?.substringBeforeLast('.') ?: return null
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            Track(
                id = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                source = TrackSource.LOCAL,
                uri = uri,
                folder = folderName,
                // artwork is loaded lazily via ArtworkLoader
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
