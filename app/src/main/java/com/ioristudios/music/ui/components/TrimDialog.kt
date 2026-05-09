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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.external.ExternalSongActions
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.rememberHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: suspend (Float, Float) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var range by remember { mutableStateOf(0.2f..0.8f) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = ExternalSongActions.sourceUri(context, song)
            if (uri != null) {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlayingPreview, range.endInclusive) {
        if (isPlayingPreview) {
            val endMs = (range.endInclusive * song.duration * 1000).toLong()
            while (isPlayingPreview) {
                currentPositionMs = exoPlayer.currentPosition
                if (exoPlayer.currentPosition >= endMs) {
                    exoPlayer.pause()
                    isPlayingPreview = false
                    break
                }
                delay(50)
            }
        } else {
            currentPositionMs = 0L
        }
    }

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

                // Range Slider with Waveform
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                    // Waveform placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonPurpleFaint)
                    ) {
                        // Simulated waveform bars
                        val rand = java.util.Random(song.title.hashCode().toLong())
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(40) { index ->
                                val height = remember(song.id) { rand.nextInt(60) + 10 }
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

                    // Playhead
                    if (isPlayingPreview) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                            val progress = (currentPositionMs / (song.duration * 1000f)).coerceIn(0f, 1f)
                            val horizontalPadding = 14.dp
                            val availableWidth = maxWidth - (horizontalPadding * 2)
                            val xOffset = horizontalPadding + (availableWidth * progress)
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = xOffset)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Color.White)
                                    .align(Alignment.CenterStart)
                            )
                        }
                    }

                    // Range Slider
                    val stepsCount = maxOf(0, song.duration.toInt() - 1)
                    RangeSlider(
                        value = range,
                        onValueChange = { 
                            haptic.performDragTick()
                            range = it 
                            if (isPlayingPreview) {
                                exoPlayer.pause()
                                isPlayingPreview = false
                            }
                        },
                        onValueChangeFinished = {
                            val startMs = (range.start * song.duration * 1000).toLong()
                            exoPlayer.seekTo(startMs)
                            exoPlayer.play()
                            isPlayingPreview = true
                        },
                        valueRange = 0f..1f,
                        steps = stepsCount,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }

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
                        onClick = {
                            if (isPlayingPreview) {
                                exoPlayer.pause()
                                isPlayingPreview = false
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SolidColor(NeonPurpleFaint)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        enabled = !isSaving
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { 
                            haptic.performHeavyClick()
                            if (isPlayingPreview) {
                                exoPlayer.pause()
                                isPlayingPreview = false
                            }
                            scope.launch {
                                isSaving = true
                                onSave(range.start, range.endInclusive)
                                isSaving = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White,
                            disabledContainerColor = NeonPurple.copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        if (!isSaving) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Save & Set", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Text("Saving...", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = NeonPurple)
                        Text("Setting up ringtone...", color = Color.White, fontWeight = FontWeight.Bold)
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
