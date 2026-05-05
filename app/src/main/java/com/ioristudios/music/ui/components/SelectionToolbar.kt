package com.ioristudios.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.SurfaceDarkCard
import com.ioristudios.music.ui.util.rememberHapticFeedback

@Composable
fun SelectionToolbar(
    isVisible: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    var showMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = selectedCount == totalCount && totalCount > 0,
                            onCheckedChange = { 
                                haptic.performClick()
                                onSelectAll(it) 
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NeonPurple,
                                uncheckedColor = Color.White.copy(alpha = 0.6f),
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    if (selectedCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                haptic.performClick()
                                onShare()
                            }) {
                                Icon(Icons.Default.Share, "Share", tint = NeonPurple)
                            }
                            
                            Box {
                                IconButton(onClick = {
                                    haptic.performClick()
                                    showMenu = true
                                }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(SurfaceDarkCard)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            haptic.performClick()
                                            onDelete()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
