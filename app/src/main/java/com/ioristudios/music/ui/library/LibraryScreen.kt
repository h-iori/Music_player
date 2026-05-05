package com.ioristudios.music.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
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
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkCard
import com.ioristudios.music.ui.theme.SurfaceGradientEnd
import com.ioristudios.music.ui.theme.SurfaceGradientStart
import com.ioristudios.music.ui.theme.TextSecondary

enum class SortMode(val label: String) {
    AZ("A–Z"),
    BY_TIME("By Time")
}

@Composable
fun LibraryScreen(
    onSongClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.AZ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val allSongs = remember { SampleData.songs }
    val filteredSongs = remember(searchQuery, sortMode, allSongs) {
        val filtered = if (searchQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortMode) {
            SortMode.AZ -> filtered.sortedBy { it.title.lowercase() }
            SortMode.BY_TIME -> filtered.sortedBy { it.duration }
        }
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Library",
                    color = CoreWhiteDim,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${allSongs.size} songs",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search + Sort row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeonSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
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
                                        sortMode = mode
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Song list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onMenuClick = { selectedSong = song }
                    )
                }

                // Bottom spacing for nav bar
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Song options sheet
        selectedSong?.let { song ->
            SongOptionsSheet(
                song = song,
                onDismiss = { selectedSong = null }
            )
        }
    }
}
