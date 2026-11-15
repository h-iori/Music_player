package com.ioristudios.music.external

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ioristudios.music.R
import com.ioristudios.music.data.repository.MusicRepository

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = DriveBackupPreferences(applicationContext)
        if (!preferences.isAutoBackupEnabled || !preferences.isDriveLinked) return Result.success()

        return runCatching {
            runCatching { setForeground(createForegroundInfo("Starting background backup...")) }
            preferences.lastStatus = "Auto backup running..."

            val access = DriveAuthManager.getAccessForBackground(applicationContext)
            val repository = MusicRepository.getInstance(applicationContext)
            val (songs, playlists) = repository.getBackupSnapshot()
            val backup = DriveBackupManager.createBackup(
                context = applicationContext,
                accessToken = access.accessToken,
                songs = songs,
                playlists = playlists,
                onProgress = { msg ->
                    runCatching {
                        setForeground(createForegroundInfo(msg))
                    }
                }
            )
            preferences.lastBackupAt = System.currentTimeMillis()
            preferences.lastStatus = "Auto backup saved ${backup.manifestName}"
            Result.success()
        }.getOrElse { error ->
            preferences.lastStatus = error.message ?: "Auto backup failed"
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(progressMessage: String): ForegroundInfo {
        val channelId = "drive_backup_channel"
        val notificationId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Drive Backup",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nightly music backup status"
                setShowBadge(false)
            }
            applicationContext.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Music Player Drive Backup")
            .setContentText(progressMessage)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
