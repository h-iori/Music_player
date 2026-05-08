package com.ioristudios.music.data.repository

import android.Manifest
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ioristudios.music.data.db.MusicDatabase
import com.ioristudios.music.data.model.HistoryEntry
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.external.RestoredContent
import com.ioristudios.music.external.RestoreResult
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = MusicDatabase(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        refreshFromDatabase()
    }

    fun refreshFromDatabase() {
        scope.launch {
            val loadedSongs = database.getSongs()
            val byId = loadedSongs.associateBy { it.id }
            _songs.value = loadedSongs
            _playlists.value = database.getPlaylists(byId)
            _history.value = database.getHistory(byId)
        }
    }

    suspend fun getBackupSnapshot(): Pair<List<Song>, List<Playlist>> = withContext(Dispatchers.IO) {
        val loadedSongs = database.getSongs()
        val byId = loadedSongs.associateBy { it.id }
        loadedSongs to database.getPlaylists(byId)
    }

    suspend fun importRestoredContent(content: RestoredContent): RestoreResult = withContext(Dispatchers.IO) {
        database.importRestoredContent(content.songs, content.playlists)
        val loadedSongs = database.getSongs()
        val byId = loadedSongs.associateBy { it.id }
        _songs.value = loadedSongs
        _playlists.value = database.getPlaylists(byId)
        RestoreResult(
            restoredSongs = content.songs.size,
            restoredPlaylists = content.playlists.size,
            skippedSongs = content.skippedSongs
        )
    }

    fun scanDevice() {
        if (!hasReadAudioPermission(appContext)) {
            refreshFromDatabase()
            return
        }
        scope.launch {
            val existing = database.getSongs().associateBy { it.id }
            val scanned = queryAudio(appContext.contentResolver).map { song ->
                val prev = existing[song.id]
                if (prev != null && prev.fileSize == song.fileSize && prev.hash != null) {
                    song.copy(hash = prev.hash)
                } else {
                    val hash = calculateHash(song)
                    song.copy(hash = hash)
                }
            }
            database.replaceSongs(scanned)
            val byId = scanned.associateBy { it.id }
            _songs.value = scanned.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            _playlists.value = database.getPlaylists(byId)
            _history.value = database.getHistory(byId)
        }
    }

    private fun calculateHash(song: Song): String? {
        val uri = song.contentUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } ?: return null
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun observeMediaStore() {
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            private var debounceJob: kotlinx.coroutines.Job? = null

            override fun onChange(selfChange: Boolean) {
                // Debounce rapid MediaStore changes — e.g. file manager bulk copy
                // fires dozens of onChange events. Without this, each one triggers
                // a full MediaStore query + SQLite write + UI recomposition cascade.
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    kotlinx.coroutines.delay(500L)
                    scanDevice()
                }
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChange(selfChange)
            }
        }
        appContext.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        scope.launch {
            database.createPlaylist(name)
            refreshFromDatabase()
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        scope.launch {
            database.renamePlaylist(playlistId, name)
            refreshFromDatabase()
        }
    }

    fun deletePlaylists(ids: Collection<Long>) {
        scope.launch {
            database.deletePlaylists(ids)
            refreshFromDatabase()
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) {
        scope.launch {
            database.addSongsToPlaylist(playlistId, songs.map { it.id })
            refreshFromDatabase()
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        scope.launch {
            database.removeSongFromPlaylist(playlistId, songId)
            refreshFromDatabase()
        }
    }

    fun reorderPlaylist(playlistId: Long, songs: List<Song>) {
        scope.launch {
            database.reorderPlaylist(playlistId, songs.map { it.id })
            refreshFromDatabase()
        }
    }

    fun recordPlayed(songId: Long) {
        scope.launch {
            database.addHistory(songId)
            val byId = _songs.value.associateBy { it.id }
            _history.value = database.getHistory(byId)
        }
    }

    fun updateSongTitle(songId: Long, title: String) {
        if (title.isBlank()) return
        scope.launch {
            database.updateSongTitle(songId, title)
            refreshFromDatabase()
        }
    }

    fun prepareDelete(songIds: Collection<Long>): MediaDeletePlan {
        val ids = songIds.toSet()
        val urisById = _songs.value
            .filter { it.id in songIds }
            .mapNotNull { song ->
                song.contentUri.takeIf(String::isNotBlank)?.let { song.id to Uri.parse(it) }
            }
            .toMap()
        val uris = urisById.values.toList()
        if (uris.isEmpty()) return MediaDeletePlan(deletedIds = emptySet(), missingIds = ids)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaDeletePlan(
                pendingIntent = MediaStore.createDeleteRequest(appContext.contentResolver, uris),
                requestedIds = ids
            )
        }

        val deleted = mutableSetOf<Long>()
        urisById.forEach { (songId, uri) ->
            try {
                if (appContext.contentResolver.delete(uri, null, null) > 0) deleted += songId
            } catch (security: RecoverableSecurityException) {
                cleanupDeletedRecords(deleted)
                return MediaDeletePlan(
                    pendingIntent = security.userAction.actionIntent,
                    requestedIds = ids,
                    deletedIds = deleted
                )
            } catch (_: SecurityException) {
                // Keep the item in the database if physical deletion failed.
            }
        }
        cleanupDeletedRecords(deleted)
        return MediaDeletePlan(deletedIds = deleted, missingIds = ids - urisById.keys)
    }

    fun completeDeleteAfterUserApproval(songIds: Collection<Long>) {
        val ids = songIds.toSet()
        scope.launch {
            val uriById = _songs.value
                .filter { it.id in ids }
                .mapNotNull { song ->
                    song.contentUri.takeIf(String::isNotBlank)?.let { song.id to Uri.parse(it) }
                }
                .toMap()
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                uriById.values.forEach { uri ->
                    runCatching { appContext.contentResolver.delete(uri, null, null) }
                }
            }
            val confirmedDeleted = uriById
                .filterValues { uri -> !mediaUriExists(uri) }
                .keys
            database.deleteSongs(confirmedDeleted)
            refreshFromDatabase()
        }
    }

    private fun mediaUriExists(uri: Uri): Boolean =
        appContext.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
            ?.use { it.moveToFirst() }
            ?: false

    private fun cleanupDeletedRecords(songIds: Collection<Long>) {
        if (songIds.isEmpty()) return
        scope.launch {
            database.deleteSongs(songIds)
            refreshFromDatabase()
        }
    }

    private fun queryAudio(contentResolver: ContentResolver): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val args = arrayOf("10000")
        return contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val volCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)
            } else -1
            @Suppress("DEPRECATION")
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val durationMs = cursor.getLong(durationCol)
                    val displayName = cursor.getString(displayCol).orEmpty()
                    val title = cursor.getString(titleCol)
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING || it.isBlank() }
                        ?: displayName.substringBeforeLast('.', displayName)
                        ?: "Unknown title"
                    val artist = cursor.getString(artistCol)
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING || it.isBlank() }
                        ?: "Unknown artist"
                    val album = cursor.getString(albumCol)
                        ?.takeUnless { it == MediaStore.UNKNOWN_STRING || it.isBlank() }
                        ?: "Unknown album"
                    val albumId = cursor.getLong(albumIdCol)
                    val albumArt = if (albumId > 0) {
                        ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId).toString()
                    } else null
                    val volumeName = if (volCol != -1) cursor.getString(volCol) else "external"
                    val songUri = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                            MediaStore.Audio.Media.getContentUri(volumeName, id)
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volumeName), id)
                        }
                        else -> {
                            ContentUris.withAppendedId(collection, id)
                        }
                    }
                    add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = durationMs / 1000L,
                            albumArt = albumArt,
                            contentUri = songUri.toString(),
                            filePath = cursor.getString(dataCol),
                            fileSize = cursor.getLong(sizeCol),
                            dateAdded = cursor.getLong(dateCol),
                            mimeType = cursor.getString(mimeCol).orEmpty()
                        )
                    )
                }
            }
        }.orEmpty()
    }

    companion object {
        private val ALBUM_ART_BASE_URI = Uri.parse("content://media/external/audio/albumart")

        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: MusicRepository(context).also { instance = it }
            }

        fun hasReadAudioPermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

data class MediaDeletePlan(
    val pendingIntent: PendingIntent? = null,
    val requestedIds: Set<Long> = emptySet(),
    val deletedIds: Set<Long> = emptySet(),
    val missingIds: Set<Long> = emptySet()
) {
    val requiresUserApproval: Boolean = pendingIntent != null
}
