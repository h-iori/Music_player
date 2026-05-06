package com.ioristudios.music.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ioristudios.music.data.model.HistoryEntry
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.Song
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE songs (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_sec INTEGER NOT NULL,
                album_art_uri TEXT,
                content_uri TEXT NOT NULL,
                file_path TEXT,
                file_size INTEGER NOT NULL,
                date_added INTEGER NOT NULL,
                mime_type TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE playlist_songs (
                playlist_id INTEGER NOT NULL,
                song_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (playlist_id, song_id),
                FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_id INTEGER NOT NULL,
                played_at INTEGER NOT NULL,
                FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_songs_title ON songs(title)")
        db.execSQL("CREATE INDEX idx_songs_artist ON songs(artist)")
        db.execSQL("CREATE INDEX idx_playlist_position ON playlist_songs(playlist_id, position)")
        db.execSQL("CREATE INDEX idx_history_played ON history(played_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS playlist_songs")
        db.execSQL("DROP TABLE IF EXISTS playlists")
        db.execSQL("DROP TABLE IF EXISTS songs")
        onCreate(db)
    }

    fun replaceSongs(songs: List<Song>) {
        writableDatabase.transaction {
            execSQL("CREATE TEMP TABLE IF NOT EXISTS scanned_song_ids (id INTEGER PRIMARY KEY)")
            delete("scanned_song_ids", null, null)
            songs.forEach { song ->
                insertWithOnConflict("songs", null, song.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
                val values = ContentValues().apply { put("id", song.id) }
                insertWithOnConflict("scanned_song_ids", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            delete("songs", "id NOT IN (SELECT id FROM scanned_song_ids)", null)
            delete("scanned_song_ids", null, null)
        }
    }

    fun getSongs(): List<Song> = readableDatabase.query(
        "songs",
        null,
        null,
        null,
        null,
        null,
        "title COLLATE NOCASE ASC"
    ).useRows { cursor -> cursor.toSong() }

    fun updateSongTitle(songId: Long, title: String) {
        val values = ContentValues().apply { put("title", title) }
        writableDatabase.update("songs", values, "id = ?", arrayOf(songId.toString()))
    }

    fun deleteSongs(songIds: Collection<Long>) {
        if (songIds.isEmpty()) return
        writableDatabase.transaction {
            songIds.forEach { songId ->
                delete("playlist_songs", "song_id = ?", arrayOf(songId.toString()))
                delete("history", "song_id = ?", arrayOf(songId.toString()))
                delete("songs", "id = ?", arrayOf(songId.toString()))
            }
        }
    }

    fun getPlaylists(songsById: Map<Long, Song>): List<Playlist> {
        val playlists = readableDatabase.query(
            "playlists",
            null,
            null,
            null,
            null,
            null,
            "modified_at DESC"
        ).useRows { cursor ->
            Playlist(
                id = cursor.getLong("id"),
                name = cursor.getString("name"),
                createdAt = cursor.getLong("created_at").formatDate(),
                modifiedAt = cursor.getLong("modified_at").formatDate()
            )
        }
        return playlists.map { playlist ->
            val songs = getPlaylistSongIds(playlist.id).mapNotNull { songsById[it] }
            playlist.copy(songs = songs)
        }
    }

    fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name.trim())
            put("created_at", now)
            put("modified_at", now)
        }
        return writableDatabase.insertWithOnConflict("playlists", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("modified_at", System.currentTimeMillis())
        }
        writableDatabase.update("playlists", values, "id = ?", arrayOf(playlistId.toString()))
    }

    fun deletePlaylists(ids: Collection<Long>) {
        writableDatabase.transaction {
            ids.forEach { id ->
                delete("playlist_songs", "playlist_id = ?", arrayOf(id.toString()))
                delete("playlists", "id = ?", arrayOf(id.toString()))
            }
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        writableDatabase.transaction {
            val existing = getPlaylistSongIds(playlistId).toMutableList()
            songIds.filterNot { existing.contains(it) }.forEach { songId -> existing += songId }
            replacePlaylistOrderLocked(playlistId, existing)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        writableDatabase.transaction {
            val next = getPlaylistSongIds(playlistId).filterNot { it == songId }
            replacePlaylistOrderLocked(playlistId, next)
        }
    }

    fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        writableDatabase.transaction { replacePlaylistOrderLocked(playlistId, songIds) }
    }

    fun addHistory(songId: Long) {
        val values = ContentValues().apply {
            put("song_id", songId)
            put("played_at", System.currentTimeMillis())
        }
        writableDatabase.insert("history", null, values)
    }

    fun getHistory(songsById: Map<Long, Song>): List<HistoryEntry> = readableDatabase.query(
        "history",
        null,
        null,
        null,
        null,
        null,
        "played_at DESC",
        "100"
    ).useRows { cursor ->
        val song = songsById[cursor.getLong("song_id")] ?: return@useRows null
        HistoryEntry(
            id = cursor.getLong("id"),
            song = song,
            playedAt = cursor.getLong("played_at")
        )
    }.filterNotNull()

    private fun getPlaylistSongIds(playlistId: Long): List<Long> = readableDatabase.query(
        "playlist_songs",
        arrayOf("song_id"),
        "playlist_id = ?",
        arrayOf(playlistId.toString()),
        null,
        null,
        "position ASC"
    ).useRows { cursor -> cursor.getLong("song_id") }

    private fun SQLiteDatabase.replacePlaylistOrderLocked(playlistId: Long, songIds: List<Long>) {
        delete("playlist_songs", "playlist_id = ?", arrayOf(playlistId.toString()))
        songIds.distinct().forEachIndexed { index, songId ->
            val values = ContentValues().apply {
                put("playlist_id", playlistId)
                put("song_id", songId)
                put("position", index)
            }
            insertWithOnConflict("playlist_songs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        val modified = ContentValues().apply { put("modified_at", System.currentTimeMillis()) }
        update("playlists", modified, "id = ?", arrayOf(playlistId.toString()))
    }

    private fun Song.toValues() = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("duration_sec", duration)
        put("album_art_uri", albumArt)
        put("content_uri", contentUri)
        put("file_path", filePath)
        put("file_size", fileSize)
        put("date_added", dateAdded)
        put("mime_type", mimeType)
    }

    private fun Cursor.toSong() = Song(
        id = getLong("id"),
        title = getString("title"),
        artist = getString("artist"),
        album = getString("album"),
        duration = getLong("duration_sec"),
        albumArt = getStringOrNull("album_art_uri"),
        contentUri = getString("content_uri"),
        filePath = getStringOrNull("file_path"),
        fileSize = getLong("file_size"),
        dateAdded = getLong("date_added"),
        mimeType = getString("mime_type")
    )

    private fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            val result = block()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
        }
    }

    private fun <T> Cursor.useRows(mapper: (Cursor) -> T): List<T> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(mapper(cursor))
        }
    }

    private fun Cursor.getString(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
    private fun Cursor.getStringOrNull(column: String): String? = getString(getColumnIndexOrThrow(column))
    private fun Cursor.getLong(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Long.formatDate(): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        private const val DB_NAME = "music.db"
        private const val DB_VERSION = 1
    }
}
