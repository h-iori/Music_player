package com.ioristudios.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VolumeViewModel : ViewModel() {
    private val _volumePercent = MutableStateFlow(100f)
    val volumePercent: StateFlow<Float> = _volumePercent.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private var hideJob: Job? = null

    fun updateVolume(delta: Float) {
        _volumePercent.value = (_volumePercent.value + delta).coerceIn(0f, 200f)
        showTemporarily()
    }

    fun setVolume(value: Float) {
        _volumePercent.value = value.coerceIn(0f, 200f)
        showTemporarily()
    }

    private fun showTemporarily() {
        _isVisible.value = true
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(2000) // Show for 2 seconds
            _isVisible.value = false
        }
    }
}
