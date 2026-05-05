package com.ioristudios.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import com.ioristudios.music.ui.VolumeViewModel

class MainActivity : ComponentActivity() {
    private lateinit var volumeViewModel: VolumeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeViewModel = ViewModelProvider(this)[VolumeViewModel::class.java]
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MusicAppRoot()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeViewModel.updateVolume(5f)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeViewModel.updateVolume(-5f)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
