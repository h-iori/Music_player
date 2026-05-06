package com.ioristudios.music.external

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.data.repository.MusicRepository
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
        val mediaStoreUpdated = if (uri != null) {
            runCatching {
                val values = ContentValues().apply { put(MediaStore.Audio.Media.TITLE, title) }
                context.contentResolver.update(uri, values, null, null) > 0
            }.getOrDefault(false)
        } else {
            false
        }
        MusicRepository.getInstance(context).updateSongTitle(song.id, title)
        return if (mediaStoreUpdated) SongEditResult.Success else SongEditResult.LocalOnly
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
    data class Failed(val reason: String) : SongEditResult
}
