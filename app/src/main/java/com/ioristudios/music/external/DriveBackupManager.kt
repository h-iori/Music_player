package com.ioristudios.music.external

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

object DriveBackupManager {
    fun createBackupDocumentIntent(): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, "music-backup-${System.currentTimeMillis()}.json")
    }

    fun openBackupDocumentIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
    }

    fun openAudioRestoreTreeIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    fun writeBackup(context: Context, uri: Uri, songs: List<Song>, playlists: List<Playlist>): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(serialize(songs, playlists).toString(2).toByteArray())
            } != null
        }.getOrDefault(false)

    private fun serialize(songs: List<Song>, playlists: List<Playlist>): JSONObject = JSONObject()
        .put("version", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("songs", JSONArray(songs.map { song ->
            JSONObject()
                .put("id", song.id)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("album", song.album)
                .put("duration", song.duration)
                .put("contentUri", song.contentUri)
                .put("mimeType", song.mimeType)
                .put("fileSize", song.fileSize)
        }))
        .put("playlists", JSONArray(playlists.map { playlist ->
            JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("createdAt", playlist.createdAt)
                .put("songIds", JSONArray(playlist.songs.map { it.id }))
        }))
}
