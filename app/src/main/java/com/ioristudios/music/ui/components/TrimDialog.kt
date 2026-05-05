package com.ioristudios.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
fun TrimDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var range by remember { mutableStateOf(0.2f..0.8f) }
    
    // Animation for appearance
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDarkSheet)
                    .border(1.dp, NeonPurpleFaint, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.ContentCut, null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                    Text(
                        text = "Trim Ringtone",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = song.title,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Waveform placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurpleFaint)
                ) {
                    // Simulated waveform bars
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(40) { index ->
                            val height = remember { (20..80).random() }
                            val isSelected = index / 40f in range
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(height.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) NeonPurple else NeonPurpleSubtle)
                            )
                        }
                    }
                }

                // Range Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeSlider(
                        value = range,
                        onValueChange = { 
                            haptic.performDragTick()
                            range = it 
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = NeonPurpleSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(range.start * song.duration),
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Duration: ${formatTime((range.endInclusive - range.start) * song.duration)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(range.endInclusive * song.duration),
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(NeonPurpleFaint)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { 
                            haptic.performHeavyClick()
                            onSave(range.start, range.endInclusive) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & Set", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "%d:%02d".format(m, s)
}
