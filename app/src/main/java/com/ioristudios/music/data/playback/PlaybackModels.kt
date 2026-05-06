package com.ioristudios.music.data.playback

import com.ioristudios.music.data.model.Song

enum class PlaybackMode {
    NORMAL,
    SHUFFLE,
    REPEAT
}

enum class PlaybackStatus {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

data class PlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val positionSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val mode: PlaybackMode = PlaybackMode.NORMAL,
    val volumePercent: Float = 100f,
    val audioSessionId: Int = 0,
    val errorMessage: String? = null
) {
    val isPlaying: Boolean = status == PlaybackStatus.PLAYING
}
