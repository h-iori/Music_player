package com.ioristudios.music.external

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.data.repository.MusicRepository
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import java.io.File

object ExternalSongActions {
    fun shareSong(context: Context, song: Song): Boolean {
        val uri = shareableUri(context, song) ?: return false
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = song.mimeType.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun shareSongs(context: Context, songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        if (songs.size == 1) return shareSong(context, songs[0])

        val uris = ArrayList<Uri>(songs.mapNotNull { shareableUri(context, it) })
        if (uris.isEmpty()) return false

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share songs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun setAsRingtone(context: Context, song: Song): RingtoneResult {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettings(context)
            return RingtoneResult.NeedsWriteSettings
        }
        val uri = song.contentUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: shareableUri(context, song)
            ?: return RingtoneResult.Failed("No accessible audio Uri")
        return runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
            RingtoneResult.Success
        }.getOrElse { RingtoneResult.Failed(it.message ?: "Unable to set ringtone") }
    }

    suspend fun trimAndSetRingtone(
        context: Context,
        song: Song,
        startFraction: Float,
        endFraction: Float
    ): RingtoneResult = RingtoneTrimmer.trimAndSetRingtone(context, song, startFraction, endFraction)

    fun updateSongTitle(context: Context, song: Song, newTitle: String): SongEditResult {
        val title = newTitle.trim()
        if (title.isBlank()) return SongEditResult.Failed("Title cannot be empty")
        
        val uri = song.contentUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return SongEditResult.Failed("No content Uri for this song")

        val hasFullAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        return try {
            val values = ContentValues().apply { 
                put(MediaStore.Audio.Media.TITLE, title)
                // Also update display name to help refresh metadata
                val ext = song.filePath?.substringAfterLast('.', "mp3")?.ifBlank { "mp3" } ?: "mp3"
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.$ext")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val pendingValues = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 1) }
                    context.contentResolver.update(uri, pendingValues, null, null)
                }
            }

            // Try updating with the direct URI first
            var rowsUpdated = context.contentResolver.update(uri, values, null, null)
            
            // Fallback: try updating by ID
            if (rowsUpdated == 0) {
                runCatching {
                    val id = ContentUris.parseId(uri)
                    rowsUpdated = context.contentResolver.update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values,
                        "${MediaStore.Audio.Media._ID} = ?",
                        arrayOf(id.toString())
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val finalValues = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                    context.contentResolver.update(uri, finalValues, null, null)
                }
            }

            if (rowsUpdated > 0) {
                MusicRepository.getInstance(context).updateSongTitle(song.id, title)
                SongEditResult.Success
            } else if (hasFullAccess && !song.filePath.isNullOrBlank()) {
                // Brute force: rename the file on disk
                val file = File(song.filePath)
                val newFile = File(file.parent, "$title.${file.extension}")
                if (file.exists() && file.renameTo(newFile)) {
                    MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null) { _, _ ->
                        MusicRepository.getInstance(context).scanDevice()
                    }
                    SongEditResult.Success
                } else {
                    SongEditResult.Failed("MediaStore update failed even with filesystem access.")
                }
            } else {
                SongEditResult.Failed("MediaStore update failed. The file might be protected or the URI is invalid.")
            }
        } catch (security: SecurityException) {
            if (hasFullAccess) {
                SongEditResult.Failed("Access denied even with All Files Access: ${security.message}")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException) {
                SongEditResult.RequiresPermission(security.userAction.actionIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                SongEditResult.RequiresPermission(pendingIntent)
            } else {
                SongEditResult.Failed(security.message ?: "Permission denied")
            }
        }
    }

    fun detailsFor(song: Song): List<Pair<String, String>> = listOf(
        "Title" to song.title,
        "Artist" to song.artist,
        "Album" to song.album.ifBlank { "Unknown album" },
        "Duration" to song.formattedDuration(),
        "Format" to song.mimeType.ifBlank { "audio/*" },
        "Size" to if (song.fileSize > 0) "${song.fileSize / 1024 / 1024} MB" else "Unknown",
        "Path" to (song.filePath ?: song.contentUri.ifBlank { "Unknown" })
    )

    internal fun requestWriteSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal fun sourceUri(context: Context, song: Song): Uri? {
        song.contentUri.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        val file = song.filePath?.let(::File)?.takeIf { it.exists() } ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun shareableUri(context: Context, song: Song): Uri? {
        song.contentUri.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        val file = song.filePath?.let(::File)?.takeIf { it.exists() } ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

sealed interface RingtoneResult {
    data object Success : RingtoneResult
    data object NeedsWriteSettings : RingtoneResult
    data class Trimmed(val uri: Uri) : RingtoneResult
    data class UnsupportedFormat(val mimeType: String) : RingtoneResult
    data class Failed(val reason: String) : RingtoneResult
}

sealed interface SongEditResult {
    data object Success : SongEditResult
    data object LocalOnly : SongEditResult
    data class RequiresPermission(val intent: PendingIntent) : SongEditResult
    data class Failed(val reason: String) : SongEditResult
}
