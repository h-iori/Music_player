package com.ioristudios.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleDark
import com.ioristudios.music.ui.theme.NeonPurpleGlow
import com.ioristudios.music.ui.theme.CoreWhite
import kotlin.math.sin

@Composable
fun VisualizerView(
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / (barCount * 1.8f)
        val spacing = canvasWidth / barCount
        val maxBarHeight = canvasHeight * 0.85f
        val centerY = canvasHeight / 2f

        for (i in 0 until barCount) {
            val normalizedPos = i.toFloat() / barCount
            val sinValue = sin(phase + normalizedPos * 6f).coerceIn(-1f, 1f)
            val sin2Value = sin(wave2 + normalizedPos * 4f + 1.5f).coerceIn(-1f, 1f)
            val combined = ((sinValue * 0.6f + sin2Value * 0.4f) * pulse)
                .coerceIn(-1f, 1f)

            val barHeight = (0.15f + 0.85f * ((combined + 1f) / 2f)) * maxBarHeight

            val x = i * spacing + (spacing - barWidth) / 2f
            val topY = centerY - barHeight / 2f

            // Glow layer
            val glowBrush = Brush.verticalGradient(
                colors = listOf(
                    NeonPurpleGlow.copy(alpha = 0.0f),
                    NeonPurpleGlow.copy(alpha = 0.25f * ((combined + 1f) / 2f)),
                    NeonPurple.copy(alpha = 0.5f * ((combined + 1f) / 2f)),
                    NeonPurpleGlow.copy(alpha = 0.25f * ((combined + 1f) / 2f)),
                    NeonPurpleGlow.copy(alpha = 0.0f),
                ),
                startY = topY - 8f,
                endY = topY + barHeight + 8f
            )
            drawRoundRect(
                brush = glowBrush,
                topLeft = Offset(x - 3f, topY - 8f),
                size = Size(barWidth + 6f, barHeight + 16f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Main bar
            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    NeonPurple,
                    CoreWhite.copy(alpha = 0.9f),
                    NeonPurple,
                ),
                startY = topY,
                endY = topY + barHeight
            )
            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
