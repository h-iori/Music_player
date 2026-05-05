package com.ioristudios.music.ui.playlists

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
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.ui.components.CreatePlaylistDialog
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
    val haptic = rememberHapticFeedback()
    var showCreateDialog by remember { mutableStateOf(false) }
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
                onCreate = { showCreateDialog = false }
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
    onToggleSelection: () -> Unit = {}
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }

    // Entrance animation
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(1f, tween(300))
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
            Icon(Icons.Filled.QueueMusic, null, tint = NeonPurple, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlist.name, color = CoreWhiteDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songs.size} songs", color = TextSecondary, fontSize = 13.sp)
        }
        if (!isSelectionMode) {
            Text(playlist.createdAt, color = TextMuted, fontSize = 11.sp)
        }
    }
}
