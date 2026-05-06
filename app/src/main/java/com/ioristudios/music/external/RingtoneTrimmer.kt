package com.ioristudios.music.external

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.ioristudios.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.max

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
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor().apply {
                setDataSource(context, sourceUri, null)
            }
            val sourceTrack = findAudioTrack(extractor)
                ?: return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.UnsupportedFormat(song.mimeType.ifBlank { "unknown" }))
            val inputFormat = extractor.getTrackFormat(sourceTrack)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!inputMime.startsWith("audio/")) {
                return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.UnsupportedFormat(inputMime))
            }

            context.contentResolver.openFileDescriptor(outputUri, "w")?.use { pfd ->
                muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxerTrack = try {
                    muxer!!.addTrack(inputFormat)
                } catch (_: RuntimeException) {
                    return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.UnsupportedFormat(inputMime))
                }
                extractor.selectTrack(sourceTrack)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val maxInputSize = if (inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    DEFAULT_BUFFER_SIZE
                }
                val buffer = ByteBuffer.allocateDirect(max(DEFAULT_BUFFER_SIZE, maxInputSize))
                val bufferInfo = MediaCodec.BufferInfo()
                muxer!!.start()

                var firstSampleTimeUs = -1L
                var wroteSamples = false
                while (true) {
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0 || sampleTimeUs > endUs) break
                    if (extractor.sampleTrackIndex != sourceTrack || sampleTimeUs < startUs) {
                        extractor.advance()
                        continue
                    }
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    if (firstSampleTimeUs < 0) firstSampleTimeUs = sampleTimeUs
                    val outputFlags = extractor.sampleFlags.toBufferInfoFlags()
                    bufferInfo.set(
                        0,
                        sampleSize,
                        sampleTimeUs - firstSampleTimeUs,
                        outputFlags
                    )
                    muxer!!.writeSampleData(muxerTrack, buffer, bufferInfo)
                    wroteSamples = true
                    extractor.advance()
                }
                muxer!!.stop()
                muxer!!.release()
                muxer = null
                if (!wroteSamples) {
                    return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.Failed("No audio samples in selected range"))
                }
            } ?: return@withContext cleanupAndReturn(context, outputUri, RingtoneResult.Failed("Unable to open ringtone output"))

            publishRingtone(context, outputUri)
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, outputUri)
            RingtoneResult.Trimmed(outputUri)
        } catch (unsupported: IllegalArgumentException) {
            cleanupAndReturn(context, outputUri, RingtoneResult.UnsupportedFormat(song.mimeType.ifBlank { unsupported.message ?: "unknown" }))
        } catch (failure: Exception) {
            cleanupAndReturn(context, outputUri, RingtoneResult.Failed(failure.message ?: "Unable to trim ringtone"))
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return null
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

    private fun Int.toBufferInfoFlags(): Int {
        var flags = 0
        if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_SYNC_FRAME
        }
        if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private const val DEFAULT_BUFFER_SIZE = 256 * 1024
    private const val MIN_CLIP_US = 1_000_000L
}
