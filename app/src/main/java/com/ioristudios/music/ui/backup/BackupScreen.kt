package com.ioristudios.music.ui.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.external.BackupSnapshot
import com.ioristudios.music.ui.theme.ErrorRed
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleFaint
import com.ioristudios.music.ui.theme.NeonPurpleGlow
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SuccessGreen
import com.ioristudios.music.ui.theme.SurfaceDarkCard
import com.ioristudios.music.ui.theme.SurfaceDarkElevated
import com.ioristudios.music.ui.theme.SurfaceGradientEnd
import com.ioristudios.music.ui.theme.SurfaceGradientStart
import com.ioristudios.music.ui.theme.TextMuted
import com.ioristudios.music.ui.theme.TextPrimary
import com.ioristudios.music.ui.theme.TextSecondary
import com.ioristudios.music.ui.util.AnimDuration
import com.ioristudios.music.ui.util.rememberHapticFeedback
import java.text.DateFormat
import java.util.Date
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val haptic = rememberHapticFeedback()
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            viewModel.handleAuthorizationResult(activity, result.data)
        }
    }
    val launchResolution: (android.app.PendingIntent) -> Unit = { pendingIntent ->
        authLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BackupHeader(onBack = onBack)
            }

            item {
                DriveStatusCard(
                    state = state,
                    onLink = {
                        if (activity != null) {
                            haptic.performClick()
                            viewModel.linkDrive(activity, launchResolution)
                        }
                    },
                    onDisconnect = {
                        haptic.performClick()
                        viewModel.disconnectDrive()
                    }
                )
            }

            item {
                AutoBackupCard(
                    enabled = state.isAutoBackupEnabled,
                    linked = state.isDriveLinked,
                    onEnabledChange = {
                        haptic.performClick()
                        viewModel.setAutoBackupEnabled(it)
                    }
                )
            }

            item {
                ActionGrid(
                    state = state,
                    onBackup = {
                        if (activity != null) {
                            haptic.performClick()
                            viewModel.backupNow(activity, launchResolution)
                        }
                    },
                    onRefresh = {
                        if (activity != null) {
                            haptic.performClick()
                            viewModel.loadBackups(activity, launchResolution)
                        }
                    },
                    onRestore = {
                        haptic.performClick()
                        showRestoreConfirm = true
                    }
                )
            }

            item {
                LatestBackupCard(snapshot = state.latestBackup)
            }

            state.error?.let { error ->
                item {
                    StatusBanner(
                        iconTint = ErrorRed,
                        icon = Icons.Filled.Error,
                        title = "Action needed",
                        message = error
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isBusy || state.isLoadingBackups,
            enter = fadeIn(tween(AnimDuration.FAST)),
            exit = fadeOut(tween(AnimDuration.FAST)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ProgressSheet(message = state.progress ?: "Working with Drive")
        }
    }

    if (showRestoreConfirm) {
        RestoreConfirmDialog(
            snapshot = state.latestBackup,
            onDismiss = { showRestoreConfirm = false },
            onConfirm = {
                showRestoreConfirm = false
                if (activity != null) viewModel.restoreLatest(activity, launchResolution)
            }
        )
    }
}

@Composable
fun FirstLaunchDrivePrompt(
    viewModel: BackupViewModel
) {
    val state by viewModel.uiState.collectAsState()
    if (state.hasSeenFirstLaunchPrompt || state.isDriveLinked) return

    val context = LocalContext.current
    val activity = context.findActivity()
    val haptic = rememberHapticFeedback()
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            viewModel.handleAuthorizationResult(activity, result.data)
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(AnimDuration.MEDIUM)) + scaleIn(tween(AnimDuration.MEDIUM)),
        exit = fadeOut(tween(AnimDuration.FAST))
    ) {
        AlertDialog(
            onDismissRequest = { viewModel.markFirstPromptSeen() },
            containerColor = SurfaceDarkElevated,
            tonalElevation = 12.dp,
            icon = {
                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = NeonPurple)
            },
            title = {
                Text("Link Google Drive", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Allow Drive access to keep songs and playlists backed up in your private app data folder.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activity != null) {
                            haptic.performClick()
                            viewModel.linkDrive(activity) { pending ->
                                authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Link Drive", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.markFirstPromptSeen() },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonPurple)
                ) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
private fun BackupHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPurple)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Backup & Restore", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Google Drive app data sync", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DriveStatusCard(
    state: BackupUiState,
    onLink: () -> Unit,
    onDisconnect: () -> Unit
) {
    BackupCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlowIcon(
                icon = if (state.isDriveLinked) Icons.Filled.CloudDone else Icons.Filled.Link,
                tint = if (state.isDriveLinked) SuccessGreen else NeonPurple
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.isDriveLinked) "Drive linked" else "Drive not linked",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    state.linkedAccount ?: "Private app folder permission is required for backup.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.isDriveLinked) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect Drive", tint = TextSecondary)
                }
            } else {
                Button(
                    onClick = onLink,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Link", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AutoBackupCard(
    enabled: Boolean,
    linked: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    BackupCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlowIcon(Icons.Filled.Schedule, NeonPurple)
            Column(modifier = Modifier.weight(1f)) {
                Text("Nightly auto backup", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Runs around 12:00 AM when network is available.", color = TextSecondary, fontSize = 13.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { if (linked) onEnabledChange(it) },
                enabled = linked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = NeonPurple,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceDarkElevated
                )
            )
        }
    }
}

@Composable
private fun ActionGrid(
    state: BackupUiState,
    onBackup: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                title = "Back up now",
                subtitle = "Songs + playlists",
                icon = Icons.Filled.CloudUpload,
                enabled = state.isDriveLinked && !state.isBusy,
                onClick = onBackup,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                title = "Restore",
                subtitle = "Latest backup",
                icon = Icons.Filled.Restore,
                enabled = state.isDriveLinked && state.latestBackup != null && !state.isBusy,
                onClick = onRestore,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = state.isDriveLinked && !state.isLoadingBackups,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeonPurpleSubtle)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Refresh Drive backups", color = TextPrimary)
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(128.dp)
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) SurfaceDarkCard else SurfaceDarkCard.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (enabled) NeonPurpleSubtle else NeonPurpleFaint)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            GlowIcon(icon, if (enabled) NeonPurple else TextMuted, size = 42)
            Column {
                Text(title, color = if (enabled) TextPrimary else TextMuted, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LatestBackupCard(snapshot: BackupSnapshot?) {
    BackupCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlowIcon(Icons.Filled.Storage, NeonPurple)
            Column(modifier = Modifier.weight(1f)) {
                Text("Latest backup", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (snapshot == null) {
                    Text("No backup has been found in Drive yet.", color = TextSecondary, fontSize = 13.sp)
                } else {
                    Text(
                        "${snapshot.songCount} songs • ${snapshot.playlistCount} playlists • ${snapshot.totalBytes.formatBytes()}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(snapshot.createdAt.formatTime(), color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Surface(
        color = SurfaceDarkElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Column {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(message, color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BackupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDarkCard,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, NeonPurpleSubtle)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun GlowIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: Int = 48
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size((size * 0.52f).dp)
                .graphicsLayer { shadowElevation = 10f }
        )
    }
}

@Composable
private fun ProgressSheet(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        color = SurfaceDarkElevated,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, NeonPurpleSubtle)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(message, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    snapshot: BackupSnapshot?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDarkElevated,
        icon = { Icon(Icons.Filled.Restore, contentDescription = null, tint = NeonPurple) },
        title = { Text("Restore latest backup?", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                snapshot?.let {
                    "This will download ${it.songCount} songs and recreate ${it.playlistCount} playlists from ${it.createdAt.formatTime()}."
                } ?: "No backup is available.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = snapshot != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(Modifier.size(8.dp))
                Text("Restore", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

private fun Long.formatTime(): String {
    if (this <= 0L) return "Never"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
}

private fun Long.formatBytes(): String {
    if (this <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val index = log10(toDouble()).div(log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = this / 1024.0.pow(index.toDouble())
    return "%.1f %s".format(value, units[index])
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
