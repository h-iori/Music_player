package com.ioristudios.music

import android.Manifest
import android.content.Intent
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

import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.playback.PlaybackService
import com.ioristudios.music.ui.VolumeViewModel

class MainActivity : ComponentActivity() {
    private lateinit var volumeViewModel: VolumeViewModel
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        MusicRepository.getInstance(this).scanDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeViewModel = ViewModelProvider(this)[VolumeViewModel::class.java]
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestRuntimePermissions()
        handleIncomingIntent(intent)
        setContent {
            MusicAppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeViewModel.updateVolume(5f)
                PlaybackService.setVolume(this, volumeViewModel.volumePercent.value)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeViewModel.updateVolume(-5f)
                PlaybackService.setVolume(this, volumeViewModel.volumePercent.value)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            PlaybackService.playUri(this, intent.data!!)
        }
    }
}
