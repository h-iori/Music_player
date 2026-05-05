package com.ioristudios.music.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.Playlist
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.ui.components.CreatePlaylistDialog
import com.ioristudios.music.ui.theme.*

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val playlists = remember { SampleData.playlists }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd))
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
                Text("Playlists", color = CoreWhiteDim, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${playlists.size} playlists", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 96.dp)
                .shadow(12.dp, CircleShape, ambientColor = NeonPurple.copy(alpha = 0.3f), spotColor = NeonPurpleGlow.copy(alpha = 0.4f)),
            containerColor = NeonPurple,
            contentColor = CoreWhite,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, "Create Playlist")
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { showCreateDialog = false }
            )
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(SurfaceDarkCard).border(1.dp, NeonPurpleFaint, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
        Text(playlist.createdAt, color = TextMuted, fontSize = 11.sp)
    }
}
