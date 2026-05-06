package com.ioristudios.music.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.ui.components.NeonSearchBar
import com.ioristudios.music.ui.components.SongOptionsSheet
import com.ioristudios.music.ui.components.SongRow
import com.ioristudios.music.ui.theme.CoreWhiteDim
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleFaint
import com.ioristudios.music.ui.theme.NeonPurpleGlow
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkCard
import com.ioristudios.music.ui.theme.SurfaceGradientEnd
import com.ioristudios.music.ui.theme.SurfaceGradientStart
import com.ioristudios.music.ui.theme.TextMuted
import com.ioristudios.music.ui.theme.TextSecondary
import com.ioristudios.music.ui.components.SelectionToolbar
import com.ioristudios.music.ui.components.ConfirmationDialog
import com.ioristudios.music.ui.util.rememberHapticFeedback
import com.ioristudios.music.ui.components.AppSidebar

enum class SortMode(val label: String) {
    AZ("A–Z"),
    BY_TIME("By Time")
}

@Composable
fun LibraryScreen(
    onSongClick: (Song) -> Unit = {},
    onAboutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val haptic = rememberHapticFeedback()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    
    val listState = rememberLazyListState()
    var showSidebar by remember { mutableStateOf(false) }

    // Back handler to close sidebar if open
    BackHandler(enabled = showSidebar) {
        showSidebar = false
    }
    
    // Animation logic for header
    val headerAlpha by remember {
        derivedStateOf {
            if (isSelectionMode) 0f
            else if (listState.firstVisibleItemIndex > 0) 0f
            else {
                val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                (1f - (scrollOffset / 300f)).coerceIn(0f, 1f)
            }
        }
    }
    
    val headerTranslationY by remember {
        derivedStateOf {
            if (isSelectionMode) -100f
            else if (listState.firstVisibleItemIndex > 0) -100f
            else {
                val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                (-scrollOffset * 0.5f).coerceIn(-100f, 0f)
            }
        }
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val allSongsSize = SampleData.songs.size

    // Back handler to exit selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SurfaceGradientStart, SurfaceGradientEnd)
                )
            )
    ) {
        @OptIn(ExperimentalFoundationApi::class)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header Item
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = headerTranslationY
                        }
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp) // Minimal top padding
                ) {
                    Box {
                        // Intense Glow Layer
                        Text(
                            text = "Music",
                            color = Color.Transparent,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = NeonPurpleGlow,
                                    blurRadius = 80f
                                )
                            )
                        )
                        // Secondary Glow Layer
                        Text(
                            text = "Music",
                            color = Color.Transparent,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = NeonPurpleGlow.copy(alpha = 0.8f),
                                    blurRadius = 40f
                                )
                            )
                        )
                        // Primary Text
                        Text(
                            text = "Music",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = NeonPurpleGlow.copy(alpha = 0.5f),
                                    blurRadius = 15f
                                )
                            )
                        )
                    }

                    Text(
                        text = "by IORI STUDIOS",
                        color = NeonPurple.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 0.dp, bottom = 4.dp)
                    )

                    Text(
                        text = "${allSongsSize} songs",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Sticky Search + Sort Row
            stickyHeader {
                AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        SurfaceGradientStart.copy(alpha = 0.95f),
                                        SurfaceGradientStart.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(
                                onClick = {
                                    haptic.performClick()
                                    showSortMenu = true
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceDarkCard)
                                    .border(1.dp, NeonPurpleSubtle, RoundedCornerShape(16.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SortByAlpha,
                                    contentDescription = "Sort",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier
                                    .background(SurfaceDarkCard)
                                    .border(1.dp, NeonPurpleFaint, RoundedCornerShape(8.dp))
                            ) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = mode.label,
                                                color = if (sortMode == mode) NeonPurple else CoreWhiteDim,
                                                fontWeight = if (sortMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            haptic.performClick()
                                            viewModel.onSortModeChange(mode)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Song list or empty state
            if (filteredSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No songs found",
                                color = TextMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Try a different search term",
                                color = TextMuted.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onMenuClick = { selectedSong = song },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedSongIds.contains(song.id),
                        onToggleSelection = { viewModel.toggleSelection(song.id) },
                        onLongClick = { viewModel.enterSelectionMode(song.id) },
                        isPlaying = song.id == SampleData.currentSong.id
                    )
                }
            }
        }

        // Selection Toolbar
        SelectionToolbar(
            isVisible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            totalCount = filteredSongs.size,
            onClose = { viewModel.exitSelectionMode() },
            onSelectAll = { if (it) viewModel.selectAll() else viewModel.deselectAll() },
            onDelete = { showDeleteConfirm = true },
            onShare = { viewModel.shareSelected() }
        )

        // Song options sheet
        selectedSong?.let { song ->
            SongOptionsSheet(
                song = song,
                onDismiss = { selectedSong = null }
            )
        }

        // Delete confirmation
        if (showDeleteConfirm) {
            ConfirmationDialog(
                title = "Delete Songs",
                message = "Are you sure you want to delete ${selectedSongIds.size} selected songs? This action cannot be undone.",
                confirmText = "Delete",
                onConfirm = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        // Hamburger Icon
        if (!isSelectionMode) {
            IconButton(
                onClick = {
                    haptic.performClick()
                    showSidebar = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                    tint = NeonPurple,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Sidebar Overlay
        AppSidebar(
            isVisible = showSidebar,
            onDismiss = { showSidebar = false },
            onBackupClick = { 
                // Backup logic could be added here
                showSidebar = false 
            },
            onAboutClick = {
                showSidebar = false
                onAboutClick()
            }
        )
    }
}
