package com.ioristudios.music

import android.app.Application
import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.external.DriveBackupScheduler

class MusicApp : Application() {
    val repository: MusicRepository by lazy { MusicRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        repository.observeMediaStore()
        repository.scanDevice()
        DriveBackupScheduler.sync(this)
        DriveBackupScheduler.checkMissedBackup(this)
    }
}
