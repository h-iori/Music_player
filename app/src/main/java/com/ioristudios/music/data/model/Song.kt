package com.ioristudios.music.data.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArt: String? = null,
    val album: String = "",
    val contentUri: String = "",
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val dateAdded: Long = 0L,
    val mimeType: String = "",
    val hash: String? = null
) {
    fun formattedDuration(): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
