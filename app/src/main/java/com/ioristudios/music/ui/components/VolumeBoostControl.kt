package com.ioristudios.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.ui.theme.CoreWhiteDim
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleFaint
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkCard
import com.ioristudios.music.ui.theme.TextSecondary

@Composable
fun VolumeBoostControl(
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var volumePercent by remember { mutableFloatStateOf(100f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Volume toggle button
        IconButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isExpanded) NeonPurpleFaint else SurfaceDarkCard
                )
                .border(
                    width = 1.dp,
                    color = if (isExpanded) NeonPurpleSubtle else NeonPurpleFaint,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = "Volume Boost",
                tint = if (isExpanded) NeonPurple else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Expandable volume control
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDarkCard)
                    .border(1.dp, NeonPurpleFaint, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume Boost",
                        color = CoreWhiteDim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${volumePercent.toInt()}%",
                        color = NeonPurple,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                NeonSlider(
                    value = volumePercent,
                    onValueChange = { volumePercent = it },
                    valueRange = 0f..200f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0%",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "100%",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "200%",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
