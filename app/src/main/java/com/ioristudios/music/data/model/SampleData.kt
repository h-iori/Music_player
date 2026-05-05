package com.ioristudios.music.data.model

object SampleData {

    val songs = listOf(
        Song(1, "Midnight Circuit", "KIRA", 234),
        Song(2, "Neon Horizons", "Synthwave Collective", 198),
        Song(3, "Electric Pulse", "Nova Ray", 312),
        Song(4, "Afterglow", "Cassandra Veil", 267),
        Song(5, "Binary Sunset", "Echo Chamber", 189),
        Song(6, "Crystalline", "Aurora Synth", 245),
        Song(7, "Dark Matter", "Phantom Grid", 278),
        Song(8, "Fragments of Light", "Lux Aeterna", 356),
        Song(9, "Gravity Well", "Stellar Drift", 203),
        Song(10, "Hologram", "Neon District", 221),
        Song(11, "Into the Void", "Black Horizon", 294),
        Song(12, "Luminance", "Crystal Method", 187),
        Song(13, "Nebula Drive", "Cosmic Engine", 265),
        Song(14, "Parallel Lines", "Velocity", 243),
        Song(15, "Quantum Break", "Digital Mirage", 318),
        Song(16, "Reverberation", "Sound Architecture", 176),
        Song(17, "Silhouette", "Midnight Protocol", 289),
        Song(18, "Turbo Flux", "Override", 231),
        Song(19, "Ultraviolet", "Prism Effect", 204),
        Song(20, "Waveform", "Signal Path", 257)
    )

    val playlists = listOf(
        Playlist(
            id = 1,
            name = "Late Night Drive",
            songs = songs.subList(0, 6),
            createdAt = "2025-12-15"
        ),
        Playlist(
            id = 2,
            name = "Cyberpunk Vibes",
            songs = songs.subList(6, 13),
            createdAt = "2026-01-03"
        ),
        Playlist(
            id = 3,
            name = "Focus Mode",
            songs = songs.subList(13, 20),
            createdAt = "2026-02-20"
        )
    )

    val currentSong = songs[2]
}
