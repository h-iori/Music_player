package com.ioristudios.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioristudios.music.playback.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * BUG 2 FIX: VolumeViewModel is now a read-through view of PlaybackService's authoritative
 * volume state, not an independent source of truth.
 *
 * PREVIOUS BEHAVIOUR (broken):
 *   The ViewModel held its own _volumePercent starting at 100f on every Activity creation.
 *   It never read the persisted value from PlaybackService state, causing the slider to
 *   show 100% even when the service had restored a different value from SharedPreferences.
 *   The first hardware key press would overwrite the restored value with 105% or 95%.
 *
 * FIX:
 *   volumePercent is derived from PlaybackService.state. The ViewModel observes the service
 *   state and shows the floating volume bar whenever the volume changes (from hardware keys,
 *   in-app slider, or ContentObserver sync). No more independent volume tracking.
 */
class VolumeViewModel : ViewModel() {
    /** Volume percentage read directly from the PlaybackService's authoritative state. */
    val volumePercent: StateFlow<Float> = PlaybackService.state
        .map { it.volumePercent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackService.state.value.volumePercent)

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private var hideJob: Job? = null
    private var lastObservedVolume: Float = PlaybackService.state.value.volumePercent

    init {
        // Watch for volume changes from ANY source (hardware keys via ContentObserver,
        // in-app slider, or focus-gain restoration) and show the floating bar briefly.
        viewModelScope.launch {
            PlaybackService.state.collect { state ->
                if (kotlin.math.abs(state.volumePercent - lastObservedVolume) > 0.5f) {
                    showTemporarily()
                }
                lastObservedVolume = state.volumePercent
            }
        }
    }

    /** Called when the user drags the floating volume bar slider. */
    fun setVolume(value: Float) {
        showTemporarily()
    }

    private fun showTemporarily() {
        _isVisible.value = true
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(2000)
            _isVisible.value = false
        }
    }
}
