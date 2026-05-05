@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ioristudios.music.ui.nowplaying

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.SampleData
import com.ioristudios.music.ui.components.NeonSlider
import com.ioristudios.music.ui.components.SongOptionsSheet
import com.ioristudios.music.ui.components.VisualizerView
import com.ioristudios.music.ui.components.VolumeBoostControl
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.pressAnimation
import com.ioristudios.music.ui.util.rememberHapticFeedback
import kotlinx.coroutines.isActive

enum class PlaybackMode { NORMAL, SHUFFLE, REPEAT }

@Composable
fun NowPlayingScreen(modifier: Modifier = Modifier) {
    val haptic = rememberHapticFeedback()
    val currentSong = remember { SampleData.currentSong }
    var isPlaying by remember { mutableStateOf(true) }
    var seekPosition by remember { mutableFloatStateOf(0.38f) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.NORMAL) }
    var isFavorite by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var lastSeekDecile by remember { mutableIntStateOf((0.38f * 10).toInt()) }

    val currentTime = remember(seekPosition, currentSong) {
        val totalSeconds = (currentSong.duration * seekPosition).toLong()
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
            // Header: "Now Playing" matching Library style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha.value },
                contentAlignment = Alignment.Center
            ) {
                // Intense Glow Layer
                Text(
                    text = "Now Playing",
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
                    text = "Now Playing",
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
                    text = "Now Playing",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = NeonPurpleGlow.copy(alpha = 0.5f),
                            blurRadius = 15f
                        )
                    )
                )

                // Options button aligned to right
                IconButton(
                    onClick = {
                        haptic.performClick()
                        showOptions = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Filled.MoreVert, "Options", tint = CoreWhiteDim, modifier = Modifier.size(20.dp))
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
                    currentSong.title,
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
                currentSong.artist,
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
                        seekPosition = newValue
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentTime, color = TextMuted, fontSize = 12.sp)
                    Text(currentSong.formattedDuration(), color = TextMuted, fontSize = 12.sp)
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
                    onClick = { haptic.performClick() },
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
                        isPlaying = !isPlaying
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
                    onClick = { haptic.performClick() },
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

            // Playback mode toggles with animated background
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .graphicsLayer { alpha = controlsAlpha.value },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                val shuffleColor by animateColorAsState(if (playbackMode == PlaybackMode.SHUFFLE) NeonPurple else CoreWhiteDim, tween(200), label = "sc")
                val shuffleBgAlpha by animateFloatAsState(
                    if (playbackMode == PlaybackMode.SHUFFLE) 1f else 0.1f,
                    tween(200), label = "sbg"
                )
                IconButton(
                    onClick = {
                        haptic.performHeavyClick()
                        playbackMode = if (playbackMode == PlaybackMode.SHUFFLE) PlaybackMode.NORMAL else PlaybackMode.SHUFFLE
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurpleFaint.copy(alpha = shuffleBgAlpha))
                        .border(1.dp, NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = shuffleColor, modifier = Modifier.size(24.dp)) }

                // Normal
                val normalBgAlpha by animateFloatAsState(
                    if (playbackMode == PlaybackMode.NORMAL) 1f else 0.1f,
                    tween(200), label = "nbg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonPurpleFaint.copy(alpha = normalBgAlpha))
                        .border(1.dp, NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            haptic.performHeavyClick()
                            playbackMode = PlaybackMode.NORMAL
                        }
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Normal",
                        color = if (playbackMode == PlaybackMode.NORMAL) NeonPurple else CoreWhiteDim,
                        fontSize = 13.sp,
                        fontWeight = if (playbackMode == PlaybackMode.NORMAL) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Repeat
                val repeatColor by animateColorAsState(if (playbackMode == PlaybackMode.REPEAT) NeonPurple else CoreWhiteDim, tween(200), label = "rc")
                val repeatBgAlpha by animateFloatAsState(
                    if (playbackMode == PlaybackMode.REPEAT) 1f else 0.1f,
                    tween(200), label = "rbg"
                )
                IconButton(
                    onClick = {
                        haptic.performHeavyClick()
                        playbackMode = if (playbackMode == PlaybackMode.REPEAT) PlaybackMode.NORMAL else PlaybackMode.REPEAT
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurpleFaint.copy(alpha = repeatBgAlpha))
                        .border(1.dp, NeonPurpleFaint.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) { Icon(if (playbackMode == PlaybackMode.REPEAT) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = repeatColor, modifier = Modifier.size(24.dp)) }
            }

            Spacer(Modifier.height(20.dp))
            // Inline volume control removed in favor of global floating bar
            // But we can keep a small indicator or button if needed. 
            // For now, let's keep it simple and clean.
        }
    }

    // Options sheet overlay
    if (showOptions) {
        SongOptionsSheet(
            song = currentSong,
            onDismiss = { showOptions = false }
        )
    }
}
