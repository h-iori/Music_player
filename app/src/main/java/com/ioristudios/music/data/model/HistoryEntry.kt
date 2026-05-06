package com.ioristudios.music.data.model

data class HistoryEntry(
    val id: Long,
    val song: Song,
    val playedAt: Long
)
