package com.ioristudios.music.external

import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

data class BackupSong(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val fileSize: Long,
    val mimeType: String,
    val displayName: String,
    val hash: String,
    val driveFileName: String
)

data class BackupPlaylist(
    val name: String,
    val songHashes: List<String>
)

data class BackupManifest(
    val version: Int,
    val createdAt: Long,
    val songs: List<BackupSong>,
    val playlists: List<BackupPlaylist>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("createdAt", createdAt)
        .put("songs", JSONArray(songs.map { song ->
            JSONObject()
                .put("title", song.title)
                .put("artist", song.artist)
                .put("album", song.album)
                .put("duration", song.duration)
                .put("fileSize", song.fileSize)
                .put("mimeType", song.mimeType)
                .put("displayName", song.displayName)
                .put("hash", song.hash)
                .put("driveFileName", song.driveFileName)
        }))
        .put("playlists", JSONArray(playlists.map { playlist ->
            JSONObject()
                .put("name", playlist.name)
                .put("songHashes", JSONArray(playlist.songHashes))
        }))

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(text: String): BackupManifest {
            val json = JSONObject(text)
            val version = json.optInt("version", CURRENT_VERSION)
            val songsJson = json.optJSONArray("songs") ?: JSONArray()
            val playlistsJson = json.optJSONArray("playlists") ?: JSONArray()
            val songs = buildList {
                for (index in 0 until songsJson.length()) {
                    val item = songsJson.getJSONObject(index)
                    val hash = item.optString("hash")
                    val driveFileName = item.optString("driveFileName")
                    if (hash.isBlank() || driveFileName.isBlank()) continue
                    add(
                        BackupSong(
                            title = item.optString("title", "Unknown title"),
                            artist = item.optString("artist", "Unknown artist"),
                            album = item.optString("album", "Unknown album"),
                            duration = item.optLong("duration", 0L),
                            fileSize = item.optLong("fileSize", 0L),
                            mimeType = item.optString("mimeType", "audio/mpeg"),
                            displayName = item.optString("displayName", "${item.optString("title", "song")}.mp3"),
                            hash = hash,
                            driveFileName = driveFileName
                        )
                    )
                }
            }
            val playlists = buildList {
                for (index in 0 until playlistsJson.length()) {
                    val item = playlistsJson.getJSONObject(index)
                    val hashesJson = item.optJSONArray("songHashes") ?: JSONArray()
                    val hashes = buildList {
                        for (hashIndex in 0 until hashesJson.length()) {
                            hashesJson.optString(hashIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    add(BackupPlaylist(name = item.optString("name", "Restored Playlist"), songHashes = hashes))
                }
            }
            return BackupManifest(
                version = version,
                createdAt = json.optLong("createdAt", 0L),
                songs = songs,
                playlists = playlists
            )
        }
    }
}

data class BackupSnapshot(
    val driveFileId: String,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val playlistCount: Int,
    val totalBytes: Long
)

data class BackupResult(
    val manifestName: String,
    val uploadedSongs: Int,
    val reusedSongs: Int,
    val skippedSongs: Int,
    val totalBytes: Long
)

data class RestoreResult(
    val restoredSongs: Int,
    val restoredPlaylists: Int,
    val skippedSongs: Int
)

internal fun buildManifest(
    songs: List<BackupSong>,
    playlists: List<Playlist>,
    songHashById: Map<Long, String>
): BackupManifest {
    val playlistPayload = playlists.map { playlist ->
        BackupPlaylist(
            name = playlist.name,
            songHashes = playlist.songs.mapNotNull { songHashById[it.id] }
        )
    }
    return BackupManifest(
        version = BackupManifest.CURRENT_VERSION,
        createdAt = System.currentTimeMillis(),
        songs = songs,
        playlists = playlistPayload
    )
}

internal fun Song.fallbackDisplayName(): String {
    val base = title.ifBlank { "song-$id" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .take(80)
    val extension = when {
        mimeType.contains("flac", ignoreCase = true) -> ".flac"
        mimeType.contains("wav", ignoreCase = true) -> ".wav"
        mimeType.contains("ogg", ignoreCase = true) -> ".ogg"
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> ".m4a"
        else -> ".mp3"
    }
    return if (base.endsWith(extension, ignoreCase = true)) base else base + extension
}
