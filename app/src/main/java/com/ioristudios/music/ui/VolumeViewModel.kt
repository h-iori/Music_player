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
 * VolumeViewModel manages volume observation and UI display for:
 * 1. Standard floating volume bar (0 - 100%)
 * 2. Extra Super Volume Booster Modal (> 100% up to 300%)
 *
 * Enforces the strict rule: The extra booster modal ONLY opens after the user
 * has reached 100% volume and activates boost beyond that.
 */
class VolumeViewModel : ViewModel() {
    /** Volume percentage read directly from PlaybackService's authoritative state (0..300%). */
    val volumePercent: StateFlow<Float> = PlaybackService.state
        .map { it.volumePercent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackService.state.value.volumePercent)

    // Standard floating bar (0-100%)
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    // Extra Volume Booster Modal (> 100% up to 300%)
    private val _isBoosterModalVisible = MutableStateFlow(false)
    val isBoosterModalVisible: StateFlow<Boolean> = _isBoosterModalVisible.asStateFlow()

    private var hideJob: Job? = null
    private var boosterHideJob: Job? = null
    private var lastObservedVolume: Float = PlaybackService.state.value.volumePercent

    init {
        viewModelScope.launch {
            var isFirstEmission = true
            PlaybackService.state.collect { state ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    lastObservedVolume = state.volumePercent
                    return@collect
                }

                val currentVol = state.volumePercent
                val changed = kotlin.math.abs(currentVol - lastObservedVolume) > 0.5f

                if (changed) {
                    if (currentVol > 100f) {
                        // Boosted domain: Keep booster modal open and reset dismiss timer
                        showBoosterModal()
                    } else {
                        // Normal domain (<= 100%):
                        // If booster modal was open and user dropped to 100% or below, close it
                        if (_isBoosterModalVisible.value) {
                            hideBoosterModal()
                        }
                        // Show normal floating bar
                        showNormalFloatingBar()
                    }
                }

                lastObservedVolume = currentVol
            }
        }
    }

    /** Called when user changes volume via UI controls. */
    fun setVolume(value: Float) {
        if (value > 100f) {
            showBoosterModal()
        } else {
            showNormalFloatingBar()
        }
    }

    /**
     * Opens the extra Super Volume Booster Modal and resets the auto-dismiss timer.
     * Only called when volume is already at 100% and user boosts beyond that.
     */
    fun showBoosterModal() {
        _isBoosterModalVisible.value = true
        _isVisible.value = false // Hide standard floating bar when booster modal is showing
        boosterHideJob?.cancel()
        boosterHideJob = viewModelScope.launch {
            delay(2500L) // 2.5 second auto-dismiss
            _isBoosterModalVisible.value = false
        }
    }

    fun hideBoosterModal() {
        boosterHideJob?.cancel()
        _isBoosterModalVisible.value = false
    }

    private fun showNormalFloatingBar() {
        // Do not show standard floating bar if booster modal is visible
        if (_isBoosterModalVisible.value) return
        _isVisible.value = true
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(2000L)
            _isVisible.value = false
        }
    }
}
