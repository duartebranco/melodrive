package com.melodrive.library

import android.content.Context
import android.net.Uri
import com.melodrive.model.Track

// stub — implemented in pr 2
object LibraryScanner {

    suspend fun scanFolder(context: Context, folderUri: Uri): List<Track> = emptyList()
}
