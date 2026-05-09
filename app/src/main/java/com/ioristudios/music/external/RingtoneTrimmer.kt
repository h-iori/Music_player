package com.ioristudios.music.external

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.ioristudios.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object RingtoneTrimmer {
    suspend fun trimAndSetRingtone(
        context: Context,
        song: Song,
        startFraction: Float,
        endFraction: Float
    ): RingtoneResult = withContext(Dispatchers.IO) {
        if (!Settings.System.canWrite(context)) {
            ExternalSongActions.requestWriteSettings(context)
            return@withContext RingtoneResult.NeedsWriteSettings
        }

        val sourceUri = ExternalSongActions.sourceUri(context, song)
            ?: return@withContext RingtoneResult.Failed("No accessible audio source")
        val durationUs = song.duration.coerceAtLeast(1L) * 1_000_000L
        val startUs = (durationUs * startFraction.coerceIn(0f, 1f)).toLong()
        val endUs = (durationUs * endFraction.coerceIn(0f, 1f)).toLong()
        if (endUs - startUs < MIN_CLIP_US) {
            return@withContext RingtoneResult.Failed("Select at least 1 second")
        }

        val outputUri = insertPendingRingtone(context, song, startUs, endUs)
            ?: return@withContext RingtoneResult.Failed("Unable to create ringtone")

        val tempFile = File(context.cacheDir, "ringtone_${System.currentTimeMillis()}.m4a")
        
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(startUs)
                        .setEndPositionUs(endUs)
                        .build()
                )
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

            val exportResult = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Result<Unit>> { continuation ->
                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(Unit))
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(exportException))
                                }
                            }
                        })
                        .build()

                    transformer.start(editedMediaItem, tempFile.absolutePath)

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                    }
                }
            }

            if (exportResult.isFailure) {
                return@withContext cleanupAndReturn(
                    context,
                    outputUri,
                    RingtoneResult.Failed(exportResult.exceptionOrNull()?.message ?: "Export failed")
                )
            }

            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.Failed("Unable to write ringtone output"))

            publishRingtone(context, outputUri)
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, outputUri)
            
            try {
                val resolver = context.contentResolver
                val uriString = outputUri.toString()
                android.provider.Settings.System.putString(resolver, "ringtone_sim2", uriString)
                android.provider.Settings.System.putString(resolver, "ringtone_2", uriString)
                android.provider.Settings.System.putString(resolver, "ringtone_sim1", uriString)
                android.provider.Settings.System.putString(resolver, "ringtone_1", uriString)
            } catch (e: Exception) {
                // Ignore undocumented key exceptions
            }
            
            return@withContext RingtoneResult.Trimmed(outputUri)
        } catch (e: Exception) {
            return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.Failed(e.message ?: "Unable to trim ringtone"))
        } finally {
            tempFile.delete()
        }
    }

    private fun insertPendingRingtone(context: Context, song: Song, startUs: Long, endUs: Long): Uri? {
        val clipSeconds = ((endUs - startUs) / 1_000_000L).coerceAtLeast(1L)
        val name = "${song.title}_${startUs / 1_000_000L}_${clipSeconds}s"
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .take(80)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            put(MediaStore.Audio.Media.DURATION, clipSeconds * 1000L)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun publishRingtone(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    private fun cleanupAndReturn(context: Context, uri: Uri, result: RingtoneResult): RingtoneResult {
        runCatching { context.contentResolver.delete(uri, null, null) }
        return result
    }

    private const val MIN_CLIP_US = 1_000_000L
}
