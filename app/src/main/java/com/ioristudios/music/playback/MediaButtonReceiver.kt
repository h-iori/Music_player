package com.ioristudios.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_UP) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlaybackService.toggle(context)
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackService.next(context)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackService.previous(context)
            KeyEvent.KEYCODE_MEDIA_STOP -> PlaybackService.pause(context)
        }
    }
}
