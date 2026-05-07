@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ioristudios.music.ui.playlists

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.data.repository.MediaDeletePlan
import com.ioristudios.music.data.repository.MusicRepository
import com.ioristudios.music.external.ExternalSongActions
import com.ioristudios.music.external.RingtoneResult
import com.ioristudios.music.playback.PlaybackService
import com.ioristudios.music.ui.components.ConfirmationDialog
import com.ioristudios.music.ui.components.SongOptionsSheet
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.rememberHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val repository = remember(context) { MusicRepository.getInstance(context) }
    val playlists by repository.playlists.collectAsState()
    val allSongs by repository.songs.collectAsState()
    val playbackState by PlaybackService.state.collectAsState()
    val playlist = playlists.find { it.id == playlistId }
    if (playlist == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Playlist not found", color = TextSecondary)
        }
        return
    }
    val songs = remember(playlist) { playlist.songs.toMutableStateList() }
    LaunchedEffect(playlist.songs) {
        songs.clear()
        songs.addAll(playlist.songs)
    }

    var showRemoveConfirm by remember { mutableStateOf(false) }
    var songToRemove by remember { mutableStateOf<Song?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var selectedSongOptions by remember { mutableStateOf<Song?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDeleteIds.isNotEmpty()) {
            repository.completeDeleteAfterUserApproval(pendingDeleteIds)
            songs.removeAll { it.id in pendingDeleteIds }
        }
        pendingDeleteIds = emptySet()
        selectedSongOptions = null
    }

    fun launchDeletePlan(plan: MediaDeletePlan) {
        if (plan.requiresUserApproval) {
            pendingDeleteIds = plan.requestedIds
            deletePermissionLauncher.launch(
                IntentSenderRequest.Builder(plan.pendingIntent!!.intentSender).build()
            )
        } else if (plan.deletedIds.isNotEmpty()) {
            songs.removeAll { it.id in plan.deletedIds }
            selectedSongOptions = null
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // For Undo logic
    var lastRemovedSong by remember { mutableStateOf<Pair<Int, Song>?>(null) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState, onMove = { from, to ->
        songs.add(to.index, songs.removeAt(from.index))
        // Haptic tick on every item boundary crossed during drag
        haptic.performDragTick()
    })

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            TopAppBar(
                title = { Text(playlist.name, color = CoreWhiteDim, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performClick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CoreWhiteDim)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performClick()
                        showAddSongsDialog = true
                    }) {
                        Icon(Icons.Filled.PlaylistAdd, "Add Songs", tint = NeonPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // Song count info
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${songs.size} songs", color = TextSecondary, fontSize = 13.sp)
                Text("Created ${playlist.createdAt}", color = TextMuted, fontSize = 11.sp)
            }

            // Song list with drag handles and haptic feedback
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    ReorderableItem(reorderState, key = song.id) { isDragging ->
                        // Scale up + elevation shadow when dragging
                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.03f else 1f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                            label = "dragScale"
                        )
                        val dragElevation by androidx.compose.animation.core.animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                            label = "dragElevation"
                        )

                        val modifierWithDrag = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performDragStart()
                            },
                            onDragStopped = {
                                haptic.performDragEnd()
                                repository.reorderPlaylist(playlistId, songs.toList())
                            }
                        )

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    val songIndex = songs.indexOf(song)
                                    lastRemovedSong = songIndex to song
                                    songs.remove(song)
                                    repository.removeSongFromPlaylist(playlistId, song.id)
                                    haptic.performHeavyClick()
                                    
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Removed \"${song.title}\"",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            lastRemovedSong?.let { (idx, s) ->
                                                songs.add(idx.coerceIn(0, songs.size), s)
                                                repository.reorderPlaylist(playlistId, songs.toList())
                                            }
                                        }
                                        lastRemovedSong = null
                                    }
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                    val color = ErrorRed.copy(alpha = 0.8f)
                                    val offset = dismissState.requireOffset()
                                    val density = androidx.compose.ui.platform.LocalDensity.current
                                    val widthDp = with(density) { offset.coerceAtLeast(0f).toDp() }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(widthDp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.wrapContentWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete, 
                                                null, 
                                                tint = Color.White,
                                                modifier = Modifier.graphicsLayer {
                                                    // Fade in text/icon as it slides
                                                    alpha = (offset / 300f).coerceIn(0f, 1f)
                                                }
                                            )
                                            Text(
                                                "Removed",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.graphicsLayer {
                                                    alpha = (offset / 300f).coerceIn(0f, 1f)
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            content = {
                                PlaylistSongRow(
                                    song = song,
                                    index = index,
                                    isDragging = isDragging,
                                    onMenuClick = {
                                        selectedSongOptions = song
                                    },
                                    onClick = {
                                        if (song.id != (playbackState.currentSong?.id ?: -1L)) {
                                            PlaybackService.playQueue(context, songs.toList(), song)
                                        }
                                        onSongClick(song)
                                    },
                                    isPlaying = song.id == playbackState.currentSong?.id,
                                    modifier = modifierWithDrag
                                        .graphicsLayer {
                                            scaleX = dragScale
                                            scaleY = dragScale
                                        }
                                        .then(
                                            if (isDragging) {
                                                Modifier.shadow(
                                                    elevation = dragElevation,
                                                    shape = RoundedCornerShape(12.dp),
                                                    ambientColor = DragElevationShadow,
                                                    spotColor = DragElevationShadow
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .animateItem()
                                )
                            }
                        )
                    }
                }
            }
        }

        // Snackbar for Undo
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .padding(horizontal = 16.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceDarkElevated,
                    contentColor = CoreWhite,
                    actionColor = NeonPurple,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(0.5.dp, NeonPurpleFaint, RoundedCornerShape(12.dp))
                )
            }
        )

        if (showAddSongsDialog) {
            com.ioristudios.music.ui.components.AddSongsToPlaylistDialog(
                onDismiss = { showAddSongsDialog = false },
                allSongs = allSongs,
                onAddSongs = { newSongs ->
                    // Add only those that aren't already in the playlist
                    val existingIds = songs.map { it.id }.toSet()
                    val songsToAdd = newSongs.filter { it.id !in existingIds }
                    songs.addAll(songsToAdd)
                    repository.addSongsToPlaylist(playlistId, songsToAdd)
                    showAddSongsDialog = false
                }
            )
        }

        selectedSongOptions?.let { song ->
            SongOptionsSheet(
                song = song,
                onDismiss = { selectedSongOptions = null },
                onShare = { ExternalSongActions.shareSong(context, song) },
                onTrimAndSetRingtone = { start, end ->
                    scope.launch {
                        val result = ExternalSongActions.trimAndSetRingtone(context, song, start, end)
                        Toast.makeText(context, result.userMessage(), Toast.LENGTH_LONG).show()
                    }
                },
                onEditName = { ExternalSongActions.updateSongTitle(context, song, it) },
                onDelete = {
                    launchDeletePlan(repository.prepareDelete(setOf(song.id)))
                }
            )
        }
    }

    // Confirmation dialog for removing a song
    if (showRemoveConfirm && songToRemove != null) {
        ConfirmationDialog(
            title = "Remove Song",
            message = "Remove \"${songToRemove!!.title}\" from this playlist?",
            confirmText = "Remove",
            onConfirm = {
                songToRemove?.let { songs.remove(it) }
                songToRemove?.let { repository.removeSongFromPlaylist(playlistId, it.id) }
                showRemoveConfirm = false
                songToRemove = null
            },
            onDismiss = {
                showRemoveConfirm = false
                songToRemove = null
            }
        )
    }
}

private fun RingtoneResult.userMessage(): String = when (this) {
    RingtoneResult.Success -> "Ringtone set"
    is RingtoneResult.Trimmed -> "Trimmed ringtone set"
    RingtoneResult.NeedsWriteSettings -> "Allow system settings access, then try again"
    is RingtoneResult.UnsupportedFormat -> "This format cannot be trimmed on this device"
    is RingtoneResult.Failed -> reason
}

@Composable
private fun PlaylistSongRow(
    song: Song,
    index: Int,
    isDragging: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()

    // Staggered Entrance Animation — optimized for enterprise performance
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * 30L)
        animatedProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    val bgColor = when {
        isDragging -> SurfaceDarkCard
        isPlaying -> NeonPurpleFaint.copy(alpha = 0.4f)
        else -> SurfaceDarkCard.copy(alpha = 0.3f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedProgress.value
                translationY = (1f - animatedProgress.value) * 40f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .then(
                if (isPlaying && !isDragging) Modifier.border(1.dp, NeonPurpleSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Drag handle - Only visible while dragging
        if (isDragging) {
            Icon(Icons.Filled.DragHandle, "Reorder", tint = NeonPurple, modifier = Modifier.size(20.dp))
        }

        // Index or Playing Icon
        Box(modifier = Modifier.width(if (isDragging) 20.dp else 24.dp), contentAlignment = Alignment.CenterStart) {
            if (isPlaying) {
                Icon(
                    Icons.Filled.GraphicEq,
                    null,
                    tint = NeonPurple,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("${index + 1}", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Music icon
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(NeonPurpleFaint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MusicNote, null, tint = NeonPurple, modifier = Modifier.size(20.dp)) }

        // Song info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                song.title,
                color = if (isPlaying) NeonPurpleLight else CoreWhiteDim,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = if (isPlaying) TextSecondary else TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(song.formattedDuration(), color = TextMuted, fontSize = 12.sp)

        // Options menu button
        IconButton(
            onClick = {
                haptic.performClick()
                onMenuClick()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.MoreVert, "Options", tint = TextSecondary, modifier = Modifier.size(24.dp))
        }
    }
}
