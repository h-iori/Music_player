package com.ioristudios.music.ui.playlists

import androidx.lifecycle.ViewModel
import com.ioristudios.music.data.model.SampleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PlaylistsViewModel : ViewModel() {
    private val _playlists = MutableStateFlow(SampleData.playlists)
    val playlists: StateFlow<List<com.ioristudios.music.data.model.Playlist>> = _playlists

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedPlaylistIds = MutableStateFlow(setOf<Long>())
    val selectedPlaylistIds: StateFlow<Set<Long>> = _selectedPlaylistIds

    fun enterSelectionMode(playlistId: Long) {
        _isSelectionMode.value = true
        _selectedPlaylistIds.value = setOf(playlistId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedPlaylistIds.value = emptySet()
    }

    fun toggleSelection(playlistId: Long) {
        _selectedPlaylistIds.update { current ->
            if (current.contains(playlistId)) {
                val next = current - playlistId
                if (next.isEmpty()) {
                    _isSelectionMode.value = false
                }
                next
            } else {
                current + playlistId
            }
        }
    }

    fun selectAll() {
        _selectedPlaylistIds.value = _playlists.value.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedPlaylistIds.value = emptySet()
    }

    fun deleteSelected() {
        // Simulation
        exitSelectionMode()
    }

    fun shareSelected() {
        // Simulation
        exitSelectionMode()
    }
}
