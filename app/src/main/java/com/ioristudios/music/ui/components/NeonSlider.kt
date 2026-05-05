package com.ioristudios.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.ioristudios.music.ui.theme.CoreWhite
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleDark
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    // Pulsing glow when actively dragging
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = tween(200),
        label = "sliderShadow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(8.dp),
                ambientColor = NeonPurple.copy(alpha = if (isDragging) 0.3f else 0.15f),
                spotColor = NeonPurple.copy(alpha = if (isDragging) 0.4f else 0.2f)
            )
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = CoreWhite,
                activeTrackColor = NeonPurple,
                inactiveTrackColor = NeonPurpleSubtle,
                activeTickColor = NeonPurple,
                inactiveTickColor = NeonPurpleSubtle,
                disabledThumbColor = NeonPurpleDark.copy(alpha = 0.5f),
                disabledActiveTrackColor = NeonPurpleDark.copy(alpha = 0.3f),
                disabledInactiveTrackColor = SurfaceDarkCard
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
