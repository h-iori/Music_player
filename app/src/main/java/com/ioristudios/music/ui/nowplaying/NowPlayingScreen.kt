@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ioristudios.music.ui.nowplaying

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.playback.PlaybackMode
import com.ioristudios.music.external.ExternalSongActions
import com.ioristudios.music.external.RingtoneResult
import com.ioristudios.music.playback.PlaybackService
import com.ioristudios.music.ui.components.NeonSlider
import com.ioristudios.music.ui.components.SongOptionsSheet
import com.ioristudios.music.ui.components.VisualizerView
import com.ioristudios.music.ui.components.VolumeBoostControl
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.pressAnimation
import com.ioristudios.music.ui.util.rememberHapticFeedback
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(modifier: Modifier = Modifier) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackState by PlaybackService.state.collectAsState()
    val currentSong = playbackState.currentSong
    val isPlaying = playbackState.isPlaying
    val seekPosition = remember(playbackState.positionSeconds, playbackState.durationSeconds) {
        if (playbackState.durationSeconds > 0) {
            playbackState.positionSeconds.toFloat() / playbackState.durationSeconds.toFloat()
        } else 0f
    }.coerceIn(0f, 1f)
    val playbackMode = playbackState.mode
    var isFavorite by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var lastSeekDecile by remember { mutableIntStateOf((seekPosition * 10).toInt()) }

    val currentTime = remember(playbackState.positionSeconds) {
        val totalSeconds = playbackState.positionSeconds
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }


    // Staggered entrance animations
    val headerAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val visualizerAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val controlsAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        headerAlpha.animateTo(1f, tween(400))
        visualizerAlpha.animateTo(1f, tween(400))
        controlsAlpha.animateTo(1f, tween(400))
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceGradientStart, SurfaceGradientEnd, NeonPurpleDark.copy(alpha = 0.05f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp)) // Top gap

            // Options button at top right
            Box(Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = {
                        haptic.performClick()
                        showOptions = true
                    },
                    modifier = Modifier
                        .size(48.dp) // Industry standard size
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Options",
                        tint = CoreWhiteDim.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .graphicsLayer {
                        alpha = visualizerAlpha.value
                        translationY = (1f - visualizerAlpha.value) * 20f
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.radialGradient(listOf(NeonPurpleFaint.copy(alpha = 0.2f), Color.Transparent)))
                    .border(1.dp, NeonPurpleFaint.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(12.dp)
            ) {
                VisualizerView(isPlaying = isPlaying, barCount = 40, modifier = Modifier.fillMaxWidth().fillMaxHeight())
            }

            Spacer(Modifier.weight(1f)) // Push everything below to the bottom

            Spacer(Modifier.height(20.dp))

            // Song title + favorite — with marquee for long titles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = controlsAlpha.value
                        translationY = (1f - controlsAlpha.value) * 20f
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    currentSong?.title ?: "No song selected",
                    color = CoreWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )

                // Favorite toggle with animated crossfade
                val favoriteScale by animateFloatAsState(
                    targetValue = if (isFavorite) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                    label = "favScale"
                )
                IconButton(
                    onClick = {
                        haptic.performHeavyClick()
                        isFavorite = !isFavorite
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = favoriteScale
                            scaleY = favoriteScale
                        }
                ) {
                    AnimatedContent(
                        targetState = isFavorite,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith
                                    (scaleOut(spring(dampingRatio = 0.5f)) + fadeOut())
                        },
                        label = "favIcon"
                    ) { favorite ->
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            "Favorite",
                            tint = if (favorite) ErrorRed else TextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                currentSong?.artist ?: "Choose a song from Library",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = controlsAlpha.value }
            )

            Spacer(Modifier.height(20.dp))

            // Seek bar with haptic on 10% boundaries
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = controlsAlpha.value }
            ) {
                NeonSlider(
                    value = seekPosition,
                    onValueChange = { newValue ->
                        val newDecile = (newValue * 10).toInt()
                        if (newDecile != lastSeekDecile) {
                            haptic.performSelection()
                            lastSeekDecile = newDecile
                        }
                        val target = ((currentSong?.duration ?: playbackState.durationSeconds) * newValue).toLong()
                        PlaybackService.seek(context, target)
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentTime, color = TextMuted, fontSize = 12.sp)
                    Text(currentSong?.formattedDuration() ?: "0:00", color = TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Transport controls with press animations
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = controlsAlpha.value },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                val prevInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = {
                        haptic.performClick()
                        PlaybackService.previous(context)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .pressAnimation(prevInteraction)
                        .clip(CircleShape)
                        .background(SurfaceDarkCard)
                        .border(1.dp, NeonPurpleFaint.copy(alpha = 0.5f), CircleShape),
                    interactionSource = prevInteraction
                ) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = CoreWhiteDim, modifier = Modifier.size(24.dp))
                }

                // Play/Pause with animated crossfade
                val playInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = {
                        haptic.performHeavyClick()
                        PlaybackService.toggle(context)
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .pressAnimation(playInteraction)
                        .shadow(12.dp, CircleShape, ambientColor = NeonPurple.copy(alpha = 0.4f), spotColor = NeonPurpleGlow.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(NeonPurple, NeonPurpleDark))),
                    interactionSource = playInteraction
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith
                                    (scaleOut(spring(dampingRatio = 0.5f)) + fadeOut())
                        },
                        label = "playPauseIcon"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = CoreWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Next
                val nextInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = {
                        haptic.performClick()
                        PlaybackService.next(context)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .pressAnimation(nextInteraction)
                        .clip(CircleShape)
                        .background(SurfaceDarkCard)
                        .border(1.dp, NeonPurpleFaint.copy(alpha = 0.5f), CircleShape),
                    interactionSource = nextInteraction
                ) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = CoreWhiteDim, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Playback mode toggles with high-contrast active states
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .graphicsLayer { alpha = controlsAlpha.value },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                val isShuffle = playbackMode == PlaybackMode.SHUFFLE
                val shuffleColor by animateColorAsState(if (isShuffle) CoreWhite else CoreWhiteDim, tween(200), label = "sc")
                val shuffleBgColor by animateColorAsState(if (isShuffle) NeonPurple else SurfaceDarkElevated, tween(200), label = "sbg")
                
                IconButton(
                    onClick = {
                            haptic.performHeavyClick()
                            PlaybackService.setMode(context, if (isShuffle) PlaybackMode.NORMAL else PlaybackMode.SHUFFLE)
                        },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shuffleBgColor)
                        .border(1.dp, if (isShuffle) NeonPurpleLight else NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = shuffleColor, modifier = Modifier.size(24.dp)) }

                // Normal
                val isNormal = playbackMode == PlaybackMode.NORMAL
                val normalColor by animateColorAsState(if (isNormal) CoreWhite else CoreWhiteDim, tween(200), label = "nc")
                val normalBgColor by animateColorAsState(if (isNormal) NeonPurple else SurfaceDarkElevated, tween(200), label = "nbg")

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(normalBgColor)
                        .border(1.dp, if (isNormal) NeonPurpleLight else NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            haptic.performHeavyClick()
                            PlaybackService.setMode(context, PlaybackMode.NORMAL)
                        }
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Normal",
                        color = normalColor,
                        fontSize = 13.sp,
                        fontWeight = if (isNormal) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Repeat
                val isRepeat = playbackMode == PlaybackMode.REPEAT
                val repeatColor by animateColorAsState(if (isRepeat) CoreWhite else CoreWhiteDim, tween(200), label = "rc")
                val repeatBgColor by animateColorAsState(if (isRepeat) NeonPurple else SurfaceDarkElevated, tween(200), label = "rbg")

                IconButton(
                    onClick = {
                            haptic.performHeavyClick()
                            PlaybackService.setMode(context, if (isRepeat) PlaybackMode.NORMAL else PlaybackMode.REPEAT)
                        },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(repeatBgColor)
                        .border(1.dp, if (isRepeat) NeonPurpleLight else NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) { Icon(if (isRepeat) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = repeatColor, modifier = Modifier.size(24.dp)) }
            }

            Spacer(Modifier.height(32.dp)) // Bottom gap
            // Inline volume control removed in favor of global floating bar
            // But we can keep a small indicator or button if needed. 
            // For now, let's keep it simple and clean.
        }
    }

    // Options sheet overlay
    if (showOptions && currentSong != null) {
        SongOptionsSheet(
            song = currentSong,
            onDismiss = { showOptions = false },
            onShare = { ExternalSongActions.shareSong(context, currentSong) },
            onTrimAndSetRingtone = { start, end ->
                scope.launch {
                    val result = ExternalSongActions.trimAndSetRingtone(context, currentSong, start, end)
                    Toast.makeText(context, result.userMessage(), Toast.LENGTH_LONG).show()
                }
            },
            onEditName = { ExternalSongActions.updateSongTitle(context, currentSong, it) }
        )
    }
}

private fun RingtoneResult.userMessage(): String = when (this) {
    RingtoneResult.Success -> "Ringtone set"
    is RingtoneResult.Trimmed -> "Trimmed ringtone set"
    RingtoneResult.NeedsWriteSettings -> "Allow system settings access, then try again"
    is RingtoneResult.UnsupportedFormat -> "This format cannot be trimmed on this device"
    is RingtoneResult.Failed -> reason
}
