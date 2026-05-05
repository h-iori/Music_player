package com.ioristudios.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.ui.theme.*
import com.ioristudios.music.ui.util.rememberHapticFeedback

@Composable
fun VolumeBoostControl(
    modifier: Modifier = Modifier,
    volumePercent: Float = 100f,
    onVolumeChange: (Float) -> Unit = {},
    isFloating: Boolean = false
) {
    val haptic = rememberHapticFeedback()
    var isExpanded by remember { mutableStateOf(!isFloating) }
    
    // Internal state for non-floating mode, otherwise uses passed parameters
    var internalVolume by remember { mutableFloatStateOf(volumePercent) }
    val currentVolume = if (isFloating) volumePercent else internalVolume

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isFloating) {
            // Volume toggle button (only in non-floating/inline mode)
            IconButton(
                onClick = {
                    haptic.performClick()
                    isExpanded = !isExpanded
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isExpanded) NeonPurpleFaint else SurfaceDarkCard)
                    .border(1.dp, if (isExpanded) NeonPurpleSubtle else NeonPurpleFaint, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Volume Boost",
                    tint = if (isExpanded) NeonPurple else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Expandable volume control or Floating Bar
        AnimatedVisibility(
            visible = isExpanded || isFloating,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isFloating) 0.dp else 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDarkCard.copy(alpha = if (isFloating) 0.95f else 1f))
                    .border(1.dp, NeonPurpleSubtle.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VolumeUp, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Volume Boost",
                            color = CoreWhiteDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "${currentVolume.toInt()}%",
                        color = NeonPurple,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                NeonSlider(
                    value = currentVolume,
                    onValueChange = { 
                        if (isFloating) onVolumeChange(it) else internalVolume = it
                    },
                    valueRange = 0f..200f
                )
            }
        }
    }
}
