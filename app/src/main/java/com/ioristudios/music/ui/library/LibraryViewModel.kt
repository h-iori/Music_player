package com.ioristudios.music.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.repository.MediaDeletePlan
import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository.getInstance(application)
    private val preferences = application.getSharedPreferences("library_preferences", Application.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortMode = MutableStateFlow(
        runCatching { SortMode.valueOf(preferences.getString("sort_mode", SortMode.AZ.name)!!) }
            .getOrDefault(SortMode.AZ)
    )
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedSongIds = MutableStateFlow(setOf<Long>())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds
    val playlists: StateFlow<List<Playlist>> = repository.playlists

    val songCount: StateFlow<Int> = repository.songs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentSongId: StateFlow<Long?> = PlaybackService.state
        .map { it.currentSong?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val filteredSongs: StateFlow<List<Song>> = combine(
        repository.songs,
        _searchQuery,
        _sortMode
    ) { allSongs, query, mode ->
        val filtered = if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
        }
        when (mode) {
            SortMode.AZ -> filtered.sortedBy { it.title.lowercase() }
            SortMode.BY_TIME -> filtered.sortedBy { it.duration }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        repository.observeMediaStore()
        repository.scanDevice()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortModeChange(mode: SortMode) {
        _sortMode.value = mode
        preferences.edit().putString("sort_mode", mode.name).apply()
    }

    fun enterSelectionMode(songId: Long) {
        _isSelectionMode.value = true
        _selectedSongIds.value = setOf(songId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongIds.value = emptySet()
    }

    fun toggleSelection(songId: Long) {
        _selectedSongIds.update { current ->
            if (current.contains(songId)) {
                val next = current - songId
                if (next.isEmpty()) {
                    _isSelectionMode.value = false
                }
                next
            } else {
                current + songId
            }
        }
    }

    fun selectAll() {
        _selectedSongIds.value = filteredSongs.value.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedSongIds.value = emptySet()
    }

    fun prepareDeleteSelected(): MediaDeletePlan {
        val plan = repository.prepareDelete(_selectedSongIds.value)
        if (!plan.requiresUserApproval) exitSelectionMode()
        return plan
    }

    fun prepareDeleteSong(songId: Long): MediaDeletePlan = repository.prepareDelete(setOf(songId))

    fun completeDeleteAfterApproval(songIds: Collection<Long>) {
        repository.completeDeleteAfterUserApproval(songIds)
        exitSelectionMode()
    }

    fun shareSelected() {
        exitSelectionMode()
    }
}
