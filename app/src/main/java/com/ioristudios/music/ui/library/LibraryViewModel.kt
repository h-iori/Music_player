package com.ioristudios.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class LibraryViewModel : ViewModel() {
    private val allSongs = SampleData.songs

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortMode = MutableStateFlow(SortMode.AZ)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedSongIds = MutableStateFlow(setOf<Long>())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds

    val filteredSongs: StateFlow<List<Song>> = combine(
        _searchQuery,
        _sortMode
    ) { query, mode ->
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
        initialValue = allSongs
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortModeChange(mode: SortMode) {
        _sortMode.value = mode
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

    fun deleteSelected() {
        // In a real app, this would update the database. 
        // For sample data, we'll just log it or simulate success.
        exitSelectionMode()
    }

    fun shareSelected() {
        // Logic to share multiple songs
        exitSelectionMode()
    }
}
