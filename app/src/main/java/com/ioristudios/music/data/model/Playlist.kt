package com.ioristudios.music.data.model

data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song> = emptyList(),
    val createdAt: String = ""
)
