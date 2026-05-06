package com.ioristudios.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistDialog(
    onDismiss: () -> Unit,
    allSongs: List<Song>,
    onAddSongs: (List<Song>) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var searchQuery by remember { mutableStateOf("") }
    val selectedSongs = remember { mutableStateListOf<Song>() }
    
    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) allSongs
        else allSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd)))
                .statusBarsPadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = CoreWhiteDim)
                    }
                    Text(
                        text = "Add Songs",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    
                    if (selectedSongs.isNotEmpty()) {
                        Button(
                            onClick = { 
                                haptic.performHeavyClick()
                                onAddSongs(selectedSongs.toList()) 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add (${selectedSongs.size})", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search songs...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = NeonPurple) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = NeonPurpleFaint,
                        cursorColor = NeonPurple,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val isSelected = selectedSongs.any { it.id == song.id }
                        SelectableSongRow(
                            song = song,
                            isSelected = isSelected,
                            onToggle = {
                                haptic.performClick()
                                if (isSelected) selectedSongs.removeAll { it.id == song.id }
                                else selectedSongs.add(song)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableSongRow(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) NeonPurpleFaint else SurfaceDarkCard)
            .border(1.dp, if (isSelected) NeonPurple else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = NeonPurple,
                uncheckedColor = TextMuted,
                checkmarkColor = Color.Black
            )
        )
        
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(NeonPurpleFaint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = NeonPurple, modifier = Modifier.size(20.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(song.title, color = CoreWhiteDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(song.artist, color = TextSecondary, fontSize = 12.sp)
        }
        
        Text(song.formattedDuration(), color = TextMuted, fontSize = 12.sp)
    }
}
