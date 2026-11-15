package com.ioristudios.music.playback.volume

/**
 * VolumeLevel defines the discrete volume tiers and millibel mapping
 * reverse-engineered from com.miui.player.effect2.volume.VolumeLevel.
 *
 * Volume is partitioned into two domains:
 * 1. Linear Stream Domain (0% - 100%): Standard Android AudioManager.STREAM_MUSIC.
 * 2. Digital Boost Domain (101% - 300%): Android STREAM_MUSIC locked to max,
 *    and android.media.audiofx.LoudnessEnhancer applies target gain in millibels (mB).
 */
enum class VolumeLevel(
    val maxValue: Int,
    val targetGainMb: Int,
    val label: String
) {
    MUTED(0, 0, "0%"),
    NORMAL(100, 0, "100%"),
    BOOST_150(150, 1000, "150%"),   // +10 dB target gain
    BOOST_200(200, 2000, "200%"),   // +20 dB target gain
    BOOST_300(300, 3000, "300%");   // +30 dB target gain (maximum safe gain)

    companion object {
        const val STEP_NORMAL = 7 // Standard +7% increment for 0-100%

        fun fromPercent(percent: Int): VolumeLevel = when {
            percent <= 0 -> MUTED
            percent <= 100 -> NORMAL
            percent <= 150 -> BOOST_150
            percent <= 200 -> BOOST_200
            else -> BOOST_300
        }

        fun isBoosted(percent: Int): Boolean = percent > NORMAL.maxValue

        /**
         * Calculates the millibel gain for any volume percentage from 0% to 300%.
         * Returns 0 for volume <= 100%.
         */
        fun calculateGainMb(percent: Int): Int {
            if (percent <= NORMAL.maxValue) return 0
            return when {
                percent <= BOOST_150.maxValue -> {
                    // Linear map from 100%..150% -> 0 mB..1000 mB
                    ((percent - 100) / 50f * 1000f).toInt().coerceIn(0, 1000)
                }
                percent <= BOOST_200.maxValue -> {
                    // Linear map from 150%..200% -> 1000 mB..2000 mB
                    (1000 + ((percent - 150) / 50f * 1000f)).toInt().coerceIn(1000, 2000)
                }
                else -> {
                    // Linear map from 200%..300% -> 2000 mB..3000 mB
                    (2000 + ((percent - 200) / 100f * 1000f)).toInt().coerceIn(2000, 3000)
                }
            }
        }

        /**
         * Computes the next volume tier on Volume Up.
         * Enforces strict threshold: jumps from 100% -> 150% only when ALREADY at 100%.
         */
        fun computeNextVolumeUp(currentVolume: Int): Int = when {
            currentVolume < NORMAL.maxValue -> Math.min(NORMAL.maxValue, currentVolume + STEP_NORMAL)
            currentVolume == NORMAL.maxValue -> BOOST_150.maxValue
            currentVolume < BOOST_150.maxValue -> BOOST_150.maxValue
            currentVolume < BOOST_200.maxValue -> BOOST_200.maxValue
            else -> BOOST_300.maxValue
        }

        /**
         * Computes the next volume tier on Volume Down.
         */
        fun computeNextVolumeDown(currentVolume: Int): Int = when {
            currentVolume > BOOST_200.maxValue -> BOOST_200.maxValue
            currentVolume > BOOST_150.maxValue -> BOOST_150.maxValue
            currentVolume > NORMAL.maxValue -> NORMAL.maxValue
            currentVolume > MUTED.maxValue -> Math.max(MUTED.maxValue, currentVolume - STEP_NORMAL)
            else -> MUTED.maxValue
        }
    }
}
