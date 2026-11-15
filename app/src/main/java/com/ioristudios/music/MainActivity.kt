package com.ioristudios.music

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.playback.PlaybackService
import com.ioristudios.music.playback.volume.VolumeLevel
import com.ioristudios.music.ui.VolumeViewModel
import androidx.activity.viewModels
import android.view.KeyEvent
import android.view.HapticFeedbackConstants

class MainActivity : ComponentActivity() {
    private val volumeViewModel: VolumeViewModel by viewModels()
    private var isExternalIntent by androidx.compose.runtime.mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        MusicRepository.getInstance(this).scanDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // BUG 2 FIX: Ensure hardware volume keys always control the music/media stream,
        // even when no audio is playing. Without this, keys may target the ringtone stream.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // REMOVED: onKeyDown override that intercepted hardware volume keys.
        // The old code consumed volume key events and only updated an internal gain value
        // via MediaPlayer.setVolume(), preventing the system STREAM_MUSIC volume from ever
        // changing. Now hardware keys work natively, and a ContentObserver in PlaybackService
        // detects the system volume change and updates the in-app slider to match.

        // Preload last played state from storage so Now Playing has immediate track & position
        PlaybackService.initialize(this)

        requestRuntimePermissions()
        handleIncomingIntent(intent)
        setContent {
            MusicAppRoot(
                isExternalIntent = isExternalIntent,
                volumeViewModel = volumeViewModel
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }

        if (!Settings.System.canWrite(this)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            isExternalIntent = true
            PlaybackService.playUri(this, intent.data!!)
        }
    }

    /**
     * Hardware Volume Key Interception reverse-engineered from MIUI Player
     * (com.miui.player.effect2.volume.VolumeController and VolumeKeyHandler).
     *
     * STRICT USER REQUIREMENT:
     *   - Volume 0-100%: Adjusts normal volume (+7% step). Booster modal remains HIDDEN.
     *   - Volume == 100%: Pressing Volume Up again detects the 100% threshold breach,
     *     steps into BOOST_150 (150%), OPENS the extra booster modal, and performs haptic feedback.
     *   - Volume > 100%: Advances through boost tiers (150% -> 200% -> 300%).
     *   - Volume Down: Steps down (300% -> 200% -> 150% -> 100%). When returning to 100%,
     *     disarms LoudnessEnhancer and DISMISSES the booster modal.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event)

        val currentPercent = PlaybackService.state.value.volumePercent.toInt()

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (currentPercent < VolumeLevel.NORMAL.maxValue) {
                // Under 100%: standard step up (+7%). Do NOT open booster modal.
                val nextVol = VolumeLevel.computeNextVolumeUp(currentPercent)
                PlaybackService.setVolume(this, nextVol.toFloat())
                return true
            } else if (currentPercent == VolumeLevel.NORMAL.maxValue) {
                // THRESHOLD TRIGGER: User is already on 100% and clicks Volume Up again!
                val nextVol = VolumeLevel.BOOST_150.maxValue
                PlaybackService.setVolume(this, nextVol.toFloat())
                volumeViewModel.showBoosterModal()
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            } else {
                // Already in boost mode: advance tiers (150 -> 200 -> 300)
                val nextVol = VolumeLevel.computeNextVolumeUp(currentPercent)
                PlaybackService.setVolume(this, nextVol.toFloat())
                volumeViewModel.showBoosterModal()
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (currentPercent > VolumeLevel.NORMAL.maxValue) {
                // In boost mode: step down through tiers (300 -> 200 -> 150 -> 100)
                val nextVol = VolumeLevel.computeNextVolumeDown(currentPercent)
                PlaybackService.setVolume(this, nextVol.toFloat())
                if (nextVol <= VolumeLevel.NORMAL.maxValue) {
                    volumeViewModel.hideBoosterModal()
                } else {
                    volumeViewModel.showBoosterModal()
                }
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            } else {
                // Below or at 100%: normal step down (-7%)
                val nextVol = VolumeLevel.computeNextVolumeDown(currentPercent)
                PlaybackService.setVolume(this, nextVol.toFloat())
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }
}
