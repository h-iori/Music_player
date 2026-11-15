package com.ioristudios.music.playback.volume

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * BoostManager encapsulates Android's native LoudnessEnhancer and coordinates
 * hardware audio stream volume with digital millibel gain.
 *
 * Reverse-engineered from com.miui.player.effect2.volume.boost.BoostManager.
 */
class BoostManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var currentGainMb: Int = 0
    private var currentVolumePercent: Int = 100

    /**
     * Rebinds the LoudnessEnhancer to the newly active MediaPlayer / AudioTrack session.
     */
    @Synchronized
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == currentSessionId && loudnessEnhancer != null) return

        releaseEnhancerOnly()
        currentSessionId = audioSessionId

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                if (currentGainMb > 0) {
                    setTargetGain(currentGainMb)
                    enabled = true
                } else {
                    enabled = false
                }
            }
            Log.d(TAG, "Attached LoudnessEnhancer to audioSessionId: $audioSessionId (gain: ${currentGainMb}mB)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach LoudnessEnhancer to audioSessionId: $audioSessionId", e)
            loudnessEnhancer = null
        }
    }

    /**
     * Applies the requested volume percentage (0..300%).
     *
     * @param volumePercent Target volume from 0 to 300
     * @return boolean True if volume is in boost domain (> 100%)
     */
    @Synchronized
    fun applyVolume(volumePercent: Int): Boolean {
        val clampedPercent = volumePercent.coerceIn(0, VolumeLevel.BOOST_300.maxValue)
        currentVolumePercent = clampedPercent
        val maxStreamVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (clampedPercent <= VolumeLevel.NORMAL.maxValue) {
            // Linear stream volume (0-100%)
            val targetStreamIndex = ((clampedPercent / 100f) * maxStreamVol).toInt().coerceIn(0, maxStreamVol)
            try {
                // Flag 0 suppresses Android's intrusive system volume dialog
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamIndex, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting stream volume to $targetStreamIndex", e)
            }

            // Disarm LoudnessEnhancer in normal range
            currentGainMb = 0
            runCatching {
                loudnessEnhancer?.apply {
                    setTargetGain(0)
                    enabled = false
                }
            }
            return false
        } else {
            // Lock Android STREAM_MUSIC to maximum (100%)
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxStreamVol, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Error locking stream volume to max", e)
            }

            // Calculate target gain in millibels
            val targetGainMb = VolumeLevel.calculateGainMb(clampedPercent)
            currentGainMb = targetGainMb

            runCatching {
                loudnessEnhancer?.apply {
                    setTargetGain(targetGainMb)
                    enabled = true
                }
            }
            Log.d(TAG, "Boost active: ${clampedPercent}% -> targetGain: ${targetGainMb} mB")
            return true
        }
    }

    /**
     * Safety reset: called on headset disconnect (ACTION_AUDIO_BECOMING_NOISY)
     * or transient focus interruptions to prevent hearing damage / speaker blowout.
     */
    @Synchronized
    fun safetyResetToNormal() {
        if (currentVolumePercent > VolumeLevel.NORMAL.maxValue) {
            Log.i(TAG, "Safety reset: resetting volume from ${currentVolumePercent}% to 100%")
            applyVolume(VolumeLevel.NORMAL.maxValue)
        }
    }

    fun getCurrentVolumePercent(): Int = currentVolumePercent

    fun getCurrentGainMb(): Int = currentGainMb

    fun isBoosted(): Boolean = currentVolumePercent > VolumeLevel.NORMAL.maxValue

    @Synchronized
    private fun releaseEnhancerOnly() {
        runCatching {
            loudnessEnhancer?.apply {
                enabled = false
                release()
            }
        }
        loudnessEnhancer = null
    }

    @Synchronized
    fun release() {
        releaseEnhancerOnly()
        currentSessionId = 0
        currentGainMb = 0
    }

    companion object {
        private const val TAG = "BoostManager"
    }
}
