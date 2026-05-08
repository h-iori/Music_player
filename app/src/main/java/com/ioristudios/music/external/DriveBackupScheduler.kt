package com.ioristudios.music.external

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

    fun checkMissedBackup(context: Context) {
        val preferences = DriveBackupPreferences(context)
        if (!preferences.isAutoBackupEnabled || !preferences.isDriveLinked) return

        val lastBackup = preferences.lastBackupAt
        val now = System.currentTimeMillis()
        val twentyFourHours = TimeUnit.DAYS.toMillis(1)

        // If the last backup was more than 24 hours ago, trigger an immediate one
        if (now - lastBackup > twentyFourHours) {
            triggerImmediateBackup(context)
        }
    }

    private fun triggerImmediateBackup(context: Context) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueue(request)
    }

    private fun millisUntilNextMidnight(): Long {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT)
        return Duration.between(now, next).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
    }
}
