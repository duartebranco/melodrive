package com.melodrive

import android.app.Application
import com.melodrive.youtube.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class MeloDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
    }
}
