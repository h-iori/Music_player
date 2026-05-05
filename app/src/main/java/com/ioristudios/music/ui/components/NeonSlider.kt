package com.ioristudios.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleDark
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.CoreWhite
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = NeonPurple.copy(alpha = 0.15f),
                spotColor = NeonPurple.copy(alpha = 0.2f)
            )
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
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
