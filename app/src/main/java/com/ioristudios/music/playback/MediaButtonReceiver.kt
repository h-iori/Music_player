package com.ioristudios.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.IntentCompat

class MediaButtonReceiver : BroadcastReceiver() {
    companion object {
        private var clickCount = 0
        private val handler = Handler(Looper.getMainLooper())
        private var pendingClickRunnable: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return
        if (event.action != KeyEvent.ACTION_UP) return

        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                clickCount++
                pendingClickRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable {
                    val count = clickCount
                    clickCount = 0
                    pendingClickRunnable = null
                    when (count) {
                        1 -> PlaybackService.toggle(context)
                        2 -> PlaybackService.next(context)
                        else -> PlaybackService.previous(context)
                    }
                }
                pendingClickRunnable = runnable
                handler.postDelayed(runnable, 350L)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlaybackService.play(context)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackService.pause(context)
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackService.next(context)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackService.previous(context)
            KeyEvent.KEYCODE_MEDIA_STOP -> PlaybackService.pause(context)
        }
    }
}
