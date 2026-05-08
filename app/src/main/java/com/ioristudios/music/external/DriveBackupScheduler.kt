package com.ioristudios.music.external

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object DriveBackupScheduler {
    private const val WORK_NAME = "drive_nightly_music_backup"

    fun sync(context: Context) {
        val preferences = DriveBackupPreferences(context)
        if (preferences.isAutoBackupEnabled && preferences.isDriveLinked) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private fun millisUntilNextMidnight(): Long {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT)
        return Duration.between(now, next).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
    }
}
