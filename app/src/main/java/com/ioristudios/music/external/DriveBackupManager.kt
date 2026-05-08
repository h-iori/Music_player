package com.ioristudios.music.external

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.Song
import java.security.MessageDigest
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriveBackupManager {
    private const val MANIFEST_PREFIX = "manifest-"
    private const val MANIFEST_SUFFIX = ".json"
    private const val SONG_PREFIX = "song-"
    private const val BACKUP_MIME = "application/json"
    private const val RESTORE_FOLDER = "Iori Restored"

    suspend fun createBackup(
        context: Context,
        accessToken: String,
        songs: List<Song>,
        playlists: List<Playlist>,
        onProgress: (String) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val client = DriveRestClient(accessToken)
        val existingFiles = client.listAppDataFiles("name contains '$SONG_PREFIX'")
            .associateBy { it.name }
        val backupSongs = mutableListOf<BackupSong>()
        val hashBySongId = mutableMapOf<Long, String>()
        var uploaded = 0
        var reused = 0
        var skipped = 0
        var totalBytes = 0L

        songs.forEachIndexed { index, song ->
            onProgress("Preparing ${index + 1} of ${songs.size}: ${song.title}")
            val uri = song.contentUri.takeIf(String::isNotBlank)?.let(Uri::parse)
            if (uri == null) {
                skipped += 1
                return@forEachIndexed
            }
            val hash = song.hash ?: appContext.contentResolver.openInputStream(uri)?.use { stream ->
                sha256(stream)
            }
            if (hash == null) {
                skipped += 1
                return@forEachIndexed
            }
            val driveFileName = "$SONG_PREFIX$hash"
            val displayName = queryDisplayName(appContext.contentResolver, uri) ?: song.fallbackDisplayName()
            val mimeType = song.mimeType.ifBlank { "audio/mpeg" }
            totalBytes += max(song.fileSize, 0L)
            hashBySongId[song.id] = hash
            backupSongs += BackupSong(
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                fileSize = song.fileSize,
                mimeType = mimeType,
                displayName = displayName,
                hash = hash,
                driveFileName = driveFileName
            )

            if (existingFiles.containsKey(driveFileName)) {
                reused += 1
            } else {
                onProgress("Uploading ${song.title}")
                val size = song.fileSize.takeIf { it > 0 } ?: -1L
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    client.uploadBinaryFile(driveFileName, mimeType, size, stream)
                    uploaded += 1
                } ?: run {
                    skipped += 1
                }
            }
        }

        val manifest = buildManifest(backupSongs, playlists, hashBySongId)
        val manifestName = "$MANIFEST_PREFIX${manifest.createdAt}$MANIFEST_SUFFIX"
        onProgress("Uploading backup manifest")
        client.uploadTextFile(manifestName, BACKUP_MIME, manifest.toJson().toString())
        BackupResult(
            manifestName = manifestName,
            uploadedSongs = uploaded,
            reusedSongs = reused,
            skippedSongs = skipped,
            totalBytes = totalBytes
        )
    }

    suspend fun listBackups(accessToken: String): List<BackupSnapshot> = withContext(Dispatchers.IO) {
        val client = DriveRestClient(accessToken)
        client.listAppDataFiles("name contains '$MANIFEST_PREFIX' and name contains '$MANIFEST_SUFFIX'")
            .mapNotNull { file ->
                runCatching {
                    val manifest = BackupManifest.fromJson(client.downloadText(file.id))
                    BackupSnapshot(
                        driveFileId = file.id,
                        name = file.name,
                        createdAt = manifest.createdAt,
                        songCount = manifest.songs.size,
                        playlistCount = manifest.playlists.size,
                        totalBytes = manifest.songs.sumOf { it.fileSize }
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun restoreBackup(
        context: Context,
        accessToken: String,
        snapshot: BackupSnapshot,
        onProgress: (String) -> Unit = {}
    ): RestoredContent = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val client = DriveRestClient(accessToken)
        val manifest = BackupManifest.fromJson(client.downloadText(snapshot.driveFileId))
        val songFiles = client.listAppDataFiles("name contains '$SONG_PREFIX'").associateBy { it.name }
        val restoredSongs = mutableListOf<Song>()
        val restoredIdsByHash = mutableMapOf<String, Long>()
        var skipped = 0

        manifest.songs.forEachIndexed { index, backupSong ->
            onProgress("Restoring ${index + 1} of ${manifest.songs.size}: ${backupSong.title}")
            
            // Duplicate Check
            val alreadyExists = checkIfExists(appContext, backupSong)
            if (alreadyExists != null) {
                restoredSongs += alreadyExists
                restoredIdsByHash[backupSong.hash] = alreadyExists.id
                return@forEachIndexed
            }

            val driveFile = songFiles[backupSong.driveFileName]
            if (driveFile == null) {
                skipped += 1
                return@forEachIndexed
            }
            
            val inserted = insertAudio(appContext, backupSong) { output ->
                client.downloadFile(driveFile.id, output)
            }
            
            if (inserted == null) {
                skipped += 1
            } else {
                restoredSongs += inserted
                restoredIdsByHash[backupSong.hash] = inserted.id
            }
        }

        val restoredPlaylists = manifest.playlists.mapNotNull { playlist ->
            val songIds = playlist.songHashes.mapNotNull { restoredIdsByHash[it] }
            if (songIds.isEmpty()) null else RestoredPlaylist(playlist.name, songIds)
        }
        RestoredContent(restoredSongs, restoredPlaylists, skipped)
    }

    private fun insertAudio(
        context: Context,
        backupSong: BackupSong,
        writeAction: (java.io.OutputStream) -> Unit
    ): Song? {
        val resolver = context.contentResolver
        val nowSeconds = System.currentTimeMillis() / 1000L
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, backupSong.displayName)
            put(MediaStore.Audio.Media.TITLE, backupSong.title)
            put(MediaStore.Audio.Media.ARTIST, backupSong.artist)
            put(MediaStore.Audio.Media.ALBUM, backupSong.album)
            put(MediaStore.Audio.Media.MIME_TYPE, backupSong.mimeType)
            put(MediaStore.Audio.Media.SIZE, backupSong.fileSize)
            put(MediaStore.Audio.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$RESTORE_FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output -> 
                writeAction(output)
            } ?: return null
            
            val finished = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finished, null, null)
            val id = ContentUris.parseId(uri)
            Song(
                id = id,
                title = backupSong.title,
                artist = backupSong.artist,
                album = backupSong.album,
                duration = backupSong.duration,
                contentUri = uri.toString(),
                fileSize = backupSong.fileSize,
                dateAdded = nowSeconds,
                mimeType = backupSong.mimeType
            )
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun checkIfExists(context: Context, backupSong: BackupSong): Song? {
        val resolver = context.contentResolver
        
        // 1. Precise Match: Search by hash in our local database
        val repository = com.ioristudios.music.data.repository.MusicRepository.getInstance(context)
        val localMatch = repository.songs.value.find { it.hash == backupSong.hash }
        if (localMatch != null) return localMatch

        // 2. Fallback: Search MediaStore by metadata (in case local DB is out of sync)
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ? AND ${MediaStore.Audio.Media.SIZE} = ?"
        val args = arrayOf(backupSong.title, backupSong.artist, backupSong.fileSize.toString())
        
        return resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val size = cursor.getLong(1)
                val duration = cursor.getLong(2)
                val uri = ContentUris.withAppendedId(collection, id)
                
                Song(
                    id = id,
                    title = cursor.getString(3) ?: backupSong.title,
                    artist = cursor.getString(4) ?: backupSong.artist,
                    album = backupSong.album,
                    duration = duration / 1000L,
                    contentUri = uri.toString(),
                    fileSize = size,
                    dateAdded = System.currentTimeMillis() / 1000L,
                    mimeType = backupSong.mimeType,
                    hash = backupSong.hash
                )
            } else null
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)
            }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class RestoredPlaylist(
    val name: String,
    val songIds: List<Long>
)

data class RestoredContent(
    val songs: List<Song>,
    val playlists: List<RestoredPlaylist>,
    val skippedSongs: Int
)
