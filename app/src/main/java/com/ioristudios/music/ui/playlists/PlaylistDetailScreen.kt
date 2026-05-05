@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.ioristudios.music.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.ui.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playlist = remember { SampleData.playlists.find { it.id == playlistId } ?: SampleData.playlists.first() }
    val songs = remember(playlist) { playlist.songs.toMutableStateList() }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState, onMove = { from, to ->
        songs.add(to.index, songs.removeAt(from.index))
    })

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd))
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = { Text(playlist.name, color = CoreWhiteDim, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CoreWhiteDim)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.PlaylistAdd, "Add Songs", tint = NeonPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )

            // Song count info
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${playlist.songs.size} songs", color = TextSecondary, fontSize = 13.sp)
                Text("Created ${playlist.createdAt}", color = TextMuted, fontSize = 11.sp)
            }

            // Song list with drag handles
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    ReorderableItem(reorderState, key = song.id) { isDragging ->
                        val modifierWithDrag = Modifier.longPressDraggableHandle()
                        PlaylistSongRow(
                            song = song,
                            index = index + 1,
                            modifier = modifierWithDrag
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSongRow(song: Song, index: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceDarkCard.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Drag handle
        Icon(Icons.Filled.DragHandle, "Reorder", tint = TextMuted, modifier = Modifier.size(20.dp))

        // Index
        Text("$index", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(24.dp))

        // Music icon
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(NeonPurpleFaint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MusicNote, null, tint = NeonPurple, modifier = Modifier.size(20.dp)) }

        // Song info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, color = CoreWhiteDim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(song.formattedDuration(), color = TextMuted, fontSize = 12.sp)

        // Remove button
        IconButton(onClick = { }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        }
    }
}
