package com.ioristudios.music

import android.app.Application
import com.ioristudios.music.data.repository.MusicRepository

class MusicApp : Application() {
    val repository: MusicRepository by lazy { MusicRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        repository.observeMediaStore()
        repository.scanDevice()
    }
}
