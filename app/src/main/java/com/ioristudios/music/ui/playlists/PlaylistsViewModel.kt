package com.ioristudios.music.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ioristudios.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository.getInstance(application)
    val playlists: StateFlow<List<com.ioristudios.music.data.model.Playlist>> = repository.playlists

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedPlaylistIds = MutableStateFlow(setOf<Long>())
    val selectedPlaylistIds: StateFlow<Set<Long>> = _selectedPlaylistIds

    init {
        repository.refreshFromDatabase()
    }

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
        _selectedPlaylistIds.value = playlists.value.map { it.id }.toSet()
    }

    fun deselectAll() {
        _selectedPlaylistIds.value = emptySet()
    }

    fun deleteSelected() {
        repository.deletePlaylists(_selectedPlaylistIds.value)
        exitSelectionMode()
    }

    fun shareSelected() {
        val selectedIds = _selectedPlaylistIds.value
        val playlistsToShare = playlists.value.filter { it.id in selectedIds }
        val songsToShare = playlistsToShare.flatMap { it.songs }.distinctBy { it.id }
        
        if (songsToShare.isNotEmpty()) {
            com.ioristudios.music.external.ExternalSongActions.shareSongs(getApplication(), songsToShare)
        }
        exitSelectionMode()
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }
}
