package com.ioristudios.music.ui.playlists

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.ui.components.ConfirmationDialog
import com.ioristudios.music.ui.components.CreatePlaylistDialog
import com.ioristudios.music.ui.components.RenamePlaylistDialog
import com.ioristudios.music.ui.components.SelectionToolbar
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.pressAnimation
import com.ioristudios.music.ui.util.rememberHapticFeedback

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPlaylistIds by viewModel.selectedPlaylistIds.collectAsState()

    // Back handler to exit selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    val listState = rememberLazyListState()
    
    // Animation logic for header (mirrors LibraryScreen)
    val headerAlpha by remember {
        derivedStateOf {
            if (isSelectionMode) 0f
            else if (listState.firstVisibleItemIndex > 0) 0f
            else {
                val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                (1f - (scrollOffset / 200f)).coerceIn(0f, 1f)
            }
        }
    }
    
    val headerTranslationY by remember {
        derivedStateOf {
            if (isSelectionMode) -60f
            else if (listState.firstVisibleItemIndex > 0) -60f
            else {
                val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                (-scrollOffset * 0.4f).coerceIn(-60f, 0f)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd))
        )
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHeavyClick()
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .shadow(12.dp, CircleShape, ambientColor = NeonPurple.copy(alpha = 0.3f), spotColor = NeonPurpleGlow.copy(alpha = 0.4f)),
                        containerColor = NeonPurple,
                        contentColor = CoreWhite,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.Add, "Create Playlist")
                    }
                }
            }
        ) { paddingValues ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .statusBarsPadding()
            ) {
                // Header [Playlist]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp) // Minimal top padding
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = headerTranslationY
                        }
                ) {
                    Text(
                        "Playlists", 
                        color = Color.White, 
                        fontSize = 32.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(
                            shadow = Shadow(
                                color = NeonPurpleGlow.copy(alpha = 0.5f),
                                blurRadius = 15f
                            )
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("${playlists.size} playlists", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    @OptIn(ExperimentalFoundationApi::class)
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedPlaylistIds.contains(playlist.id),
                            onClick = {
                                haptic.performClick()
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(playlist.id)
                                } else {
                                    onPlaylistClick(playlist)
                                }
                            },
                            onLongClick = {
                                haptic.performHeavyClick()
                                viewModel.enterSelectionMode(playlist.id)
                            },
                            onToggleSelection = { viewModel.toggleSelection(playlist.id) },
                            onRename = { playlistToRename = playlist },
                            onDelete = { playlistToDelete = playlist },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        SelectionToolbar(
            isVisible = isSelectionMode,
            selectedCount = selectedPlaylistIds.size,
            totalCount = playlists.size,
            onClose = { viewModel.exitSelectionMode() },
            onSelectAll = { if (it) viewModel.selectAll() else viewModel.deselectAll() },
            onDelete = { viewModel.deleteSelected() },
            onShare = { viewModel.shareSelected() }
        )

        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                }
            )
        }

        if (playlistToRename != null) {
            RenamePlaylistDialog(
                initialName = playlistToRename!!.name,
                onDismiss = { playlistToRename = null },
                onRename = { newName ->
                    viewModel.renamePlaylist(playlistToRename!!.id, newName) { success ->
                        if (success) {
                            Toast.makeText(context, "Playlist renamed", Toast.LENGTH_SHORT).show()
                            playlistToRename = null
                        } else {
                            Toast.makeText(context, "A playlist with this name already exists", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (playlistToDelete != null) {
            ConfirmationDialog(
                title = "Delete Playlist",
                message = "Are you sure you want to delete \"${playlistToDelete!!.name}\"?",
                confirmText = "Delete",
                onConfirm = {
                    val id = playlistToDelete!!.id
                    viewModel.deleteSelected()
                    viewModel.enterSelectionMode(id)
                    viewModel.deleteSelected()
                    playlistToDelete = null
                },
                onDismiss = { playlistToDelete = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }

    // Entrance animation — runs once per item, not on every scroll recycle
    var hasAnimated by remember(playlist.id) { mutableStateOf(false) }
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(playlist.id) {
        if (!hasAnimated) {
            animatedProgress.animateTo(1f, tween(300))
            hasAnimated = true
        } else {
            animatedProgress.snapTo(1f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedProgress.value
                translationY = (1f - animatedProgress.value) * 30f
            }
            .pressAnimation(interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) NeonPurpleFaint else SurfaceDarkCard)
            .border(
                1.dp, 
                if (isSelected) NeonPurple else NeonPurpleFaint, 
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { 
                    haptic.performClick()
                    onToggleSelection() 
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonPurple,
                    uncheckedColor = Color.White.copy(alpha = 0.4f),
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(NeonPurpleFaint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = NeonPurple, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlist.name, color = CoreWhiteDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songs.size} songs", color = TextSecondary, fontSize = 13.sp)
        }
        if (!isSelectionMode) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = {
                        haptic.performClick()
                        menuExpanded = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = CoreWhiteDim.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(SurfaceDarkSheet)
                        .border(1.dp, NeonPurpleFaint, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = CoreWhiteDim) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = NeonPurple) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red.copy(alpha = 0.85f)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.85f)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
