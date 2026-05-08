package com.ioristudios.music.external

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ioristudios.music.data.repository.MusicRepository

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = DriveBackupPreferences(applicationContext)
        if (!preferences.isAutoBackupEnabled || !preferences.isDriveLinked) return Result.success()

        return runCatching {
            val access = DriveAuthManager.getAccessForBackground(applicationContext)
            val repository = MusicRepository.getInstance(applicationContext)
            val (songs, playlists) = repository.getBackupSnapshot()
            val backup = DriveBackupManager.createBackup(
                context = applicationContext,
                accessToken = access.accessToken,
                songs = songs,
                playlists = playlists
            )
            preferences.lastBackupAt = System.currentTimeMillis()
            preferences.lastStatus = "Auto backup saved ${backup.manifestName}"
            Result.success()
        }.getOrElse { error ->
            preferences.lastStatus = error.message ?: "Auto backup failed"
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
