package com.ioristudios.music.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.ui.components.NeonSlider
import com.ioristudios.music.ui.components.VisualizerView
import com.ioristudios.music.ui.components.VolumeBoostControl
import com.ioristudios.music.ui.theme.*

enum class PlaybackMode { NORMAL, SHUFFLE, REPEAT }

@Composable
fun NowPlayingScreen(modifier: Modifier = Modifier) {
    val currentSong = remember { SampleData.currentSong }
    var isPlaying by remember { mutableStateOf(true) }
    var seekPosition by remember { mutableFloatStateOf(0.38f) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.NORMAL) }

    val currentTime = remember(seekPosition, currentSong) {
        val totalSeconds = (currentSong.duration * seekPosition).toLong()
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd, NeonPurpleDark.copy(alpha = 0.05f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("NOW PLAYING", color = NeonPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))

            // Visualizer container
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.radialGradient(listOf(NeonPurpleFaint, Color.Transparent)))
                    .border(1.dp, NeonPurpleFaint, RoundedCornerShape(20.dp)).padding(16.dp)
            ) {
                VisualizerView(isPlaying = isPlaying, barCount = 32, modifier = Modifier.fillMaxWidth().height(200.dp))
            }

            Spacer(Modifier.height(32.dp))
            Text(currentSong.title, color = CoreWhiteDim, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(currentSong.artist, color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))

            // Seek bar
            Column(Modifier.fillMaxWidth()) {
                NeonSlider(value = seekPosition, onValueChange = { seekPosition = it })
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentTime, color = TextMuted, fontSize = 12.sp)
                    Text(currentSong.formattedDuration(), color = TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Transport controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { }, modifier = Modifier.size(56.dp).clip(CircleShape).background(SurfaceDarkCard).border(1.dp, NeonPurpleFaint, CircleShape)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = CoreWhiteDim, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.size(72.dp).shadow(12.dp, CircleShape, ambientColor = NeonPurple.copy(alpha = 0.4f), spotColor = NeonPurpleGlow.copy(alpha = 0.5f))
                        .clip(CircleShape).background(Brush.radialGradient(listOf(NeonPurple, NeonPurpleDark)))
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play", tint = CoreWhite, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { }, modifier = Modifier.size(56.dp).clip(CircleShape).background(SurfaceDarkCard).border(1.dp, NeonPurpleFaint, CircleShape)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = CoreWhiteDim, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Playback mode toggles
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val shuffleColor by animateColorAsState(if (playbackMode == PlaybackMode.SHUFFLE) NeonPurple else TextMuted, tween(200), label = "sc")
                IconButton(
                    onClick = { playbackMode = if (playbackMode == PlaybackMode.SHUFFLE) PlaybackMode.NORMAL else PlaybackMode.SHUFFLE },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (playbackMode == PlaybackMode.SHUFFLE) NeonPurpleFaint else Color.Transparent)
                ) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = shuffleColor, modifier = Modifier.size(24.dp)) }

                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (playbackMode == PlaybackMode.NORMAL) NeonPurpleFaint else Color.Transparent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { playbackMode = PlaybackMode.NORMAL }.defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center
                ) { Text("Normal", color = if (playbackMode == PlaybackMode.NORMAL) NeonPurple else TextMuted, fontSize = 14.sp, fontWeight = if (playbackMode == PlaybackMode.NORMAL) FontWeight.SemiBold else FontWeight.Normal) }

                val repeatColor by animateColorAsState(if (playbackMode == PlaybackMode.REPEAT) NeonPurple else TextMuted, tween(200), label = "rc")
                IconButton(
                    onClick = { playbackMode = if (playbackMode == PlaybackMode.REPEAT) PlaybackMode.NORMAL else PlaybackMode.REPEAT },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (playbackMode == PlaybackMode.REPEAT) NeonPurpleFaint else Color.Transparent)
                ) { Icon(if (playbackMode == PlaybackMode.REPEAT) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = repeatColor, modifier = Modifier.size(24.dp)) }
            }

            Spacer(Modifier.height(24.dp))
            VolumeBoostControl()
            Spacer(Modifier.height(24.dp))
        }
    }
}
