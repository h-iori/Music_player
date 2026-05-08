package com.ioristudios.music.ui.backup

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.external.BackupSnapshot
import com.ioristudios.music.external.DriveAccess
import com.ioristudios.music.external.DriveAuthManager
import com.ioristudios.music.external.DriveBackupManager
import com.ioristudios.music.external.DriveBackupPreferences
import com.ioristudios.music.external.DriveBackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = DriveBackupPreferences(application)
    private val repository = MusicRepository.getInstance(application)
    private var pendingAccessAction: ((DriveAccess) -> Unit)? = null

    private val _uiState = MutableStateFlow(
        BackupUiState(
            isDriveLinked = preferences.isDriveLinked,
            linkedAccount = preferences.linkedAccountHint,
            isAutoBackupEnabled = preferences.isAutoBackupEnabled,
            hasSeenFirstLaunchPrompt = preferences.hasSeenFirstLaunchPrompt,
            lastBackupAt = preferences.lastBackupAt,
            lastRestoreAt = preferences.lastRestoreAt,
            status = preferences.lastStatus
        )
    )
    val uiState: StateFlow<BackupUiState> = _uiState

    fun markFirstPromptSeen() {
        preferences.hasSeenFirstLaunchPrompt = true
        _uiState.update { it.copy(hasSeenFirstLaunchPrompt = true) }
    }

    fun linkDrive(activity: Activity, onResolutionRequired: (PendingIntent) -> Unit) {
        requestAccess(
            activity = activity,
            onResolutionRequired = onResolutionRequired,
            action = { access ->
                markLinked(access)
                loadBackupsWithAccess(access)
            }
        )
    }

    fun handleAuthorizationResult(activity: Activity, data: Intent?) {
        DriveAuthManager.handleAuthorizationResult(
            activity = activity,
            data = data,
            onReady = { access ->
                val action = pendingAccessAction
                pendingAccessAction = null
                markLinked(access)
                action?.invoke(access) ?: loadBackupsWithAccess(access)
            },
            onError = { error ->
                pendingAccessAction = null
                showError(error)
            }
        )
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        preferences.isAutoBackupEnabled = enabled
        _uiState.update { it.copy(isAutoBackupEnabled = enabled) }
        DriveBackupScheduler.sync(getApplication())
    }

    fun loadBackups(activity: Activity, onResolutionRequired: (PendingIntent) -> Unit) {
        requestAccess(activity, onResolutionRequired) { access ->
            loadBackupsWithAccess(access)
        }
    }

    fun backupNow(activity: Activity, onResolutionRequired: (PendingIntent) -> Unit) {
        requestAccess(activity, onResolutionRequired) { access ->
            viewModelScope.launch {
                _uiState.update { it.copy(isBusy = true, progress = "Preparing full library backup", error = null) }
                runCatching {
                    val (songs, playlists) = repository.getBackupSnapshot()
                    DriveBackupManager.createBackup(
                        context = getApplication(),
                        accessToken = access.accessToken,
                        songs = songs,
                        playlists = playlists,
                        onProgress = { message -> _uiState.update { it.copy(progress = message) } }
                    )
                }.onSuccess { result ->
                    preferences.lastBackupAt = System.currentTimeMillis()
                    preferences.lastStatus = "Backed up ${result.uploadedSongs + result.reusedSongs} songs"
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = null,
                            lastBackupAt = preferences.lastBackupAt,
                            status = preferences.lastStatus
                        )
                    }
                    loadBackupsWithAccess(access)
                }.onFailure { error ->
                    showError(error)
                }
            }
        }
    }

    fun restoreLatest(activity: Activity, onResolutionRequired: (PendingIntent) -> Unit) {
        val snapshot = _uiState.value.latestBackup ?: return
        requestAccess(activity, onResolutionRequired) { access ->
            viewModelScope.launch {
                _uiState.update { it.copy(isBusy = true, progress = "Preparing restore", error = null) }
                runCatching {
                    val content = DriveBackupManager.restoreBackup(
                        context = getApplication(),
                        accessToken = access.accessToken,
                        snapshot = snapshot,
                        onProgress = { message -> _uiState.update { it.copy(progress = message) } }
                    )
                    repository.importRestoredContent(content)
                }.onSuccess { result ->
                    preferences.lastRestoreAt = System.currentTimeMillis()
                    preferences.lastStatus = "Restored ${result.restoredSongs} songs and ${result.restoredPlaylists} playlists"
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = null,
                            lastRestoreAt = preferences.lastRestoreAt,
                            status = preferences.lastStatus
                        )
                    }
                }.onFailure { error ->
                    showError(error)
                }
            }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            runCatching { DriveAuthManager.revoke(getApplication()) }
            preferences.clearLink()
            DriveBackupScheduler.cancel(getApplication())
            _uiState.update {
                it.copy(
                    isDriveLinked = false,
                    linkedAccount = null,
                    isAutoBackupEnabled = false,
                    backups = emptyList(),
                    latestBackup = null,
                    status = "Drive disconnected",
                    error = null
                )
            }
        }
    }

    private fun requestAccess(
        activity: Activity,
        onResolutionRequired: (PendingIntent) -> Unit,
        action: (DriveAccess) -> Unit
    ) {
        _uiState.update { it.copy(error = null) }
        pendingAccessAction = action
        DriveAuthManager.requestAuthorization(
            activity = activity,
            onReady = { access ->
                pendingAccessAction = null
                markLinked(access)
                action(access)
            },
            onResolutionRequired = onResolutionRequired,
            onError = { error ->
                pendingAccessAction = null
                showError(error)
            }
        )
    }

    private fun loadBackupsWithAccess(access: DriveAccess) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBackups = true, error = null, progress = "Checking Drive backups") }
            runCatching { DriveBackupManager.listBackups(access.accessToken) }
                .onSuccess { backups ->
                    _uiState.update {
                        it.copy(
                            isLoadingBackups = false,
                            progress = null,
                            backups = backups,
                            latestBackup = backups.firstOrNull(),
                            status = if (backups.isEmpty()) "No Drive backup found yet" else "Latest Drive backup is ready"
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    private fun markLinked(access: DriveAccess) {
        preferences.isDriveLinked = true
        preferences.hasSeenFirstLaunchPrompt = true
        preferences.linkedAccountHint = access.accountName
        _uiState.update {
            it.copy(
                isDriveLinked = true,
                hasSeenFirstLaunchPrompt = true,
                linkedAccount = access.accountName,
                status = "Drive linked",
                error = null
            )
        }
        DriveBackupScheduler.sync(getApplication())
    }

    private fun showError(error: Throwable) {
        val message = error.message ?: "Drive operation failed"
        preferences.lastStatus = message
        _uiState.update {
            it.copy(
                isBusy = false,
                isLoadingBackups = false,
                progress = null,
                error = message,
                status = message
            )
        }
    }
}

data class BackupUiState(
    val isDriveLinked: Boolean = false,
    val linkedAccount: String? = null,
    val isAutoBackupEnabled: Boolean = false,
    val hasSeenFirstLaunchPrompt: Boolean = false,
    val isBusy: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val progress: String? = null,
    val status: String = "",
    val error: String? = null,
    val backups: List<BackupSnapshot> = emptyList(),
    val latestBackup: BackupSnapshot? = null,
    val lastBackupAt: Long = 0L,
    val lastRestoreAt: Long = 0L
)
