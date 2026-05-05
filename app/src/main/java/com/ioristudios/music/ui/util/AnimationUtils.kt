package com.ioristudios.music.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Standard animation durations */
object AnimDuration {
    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 500
    const val STAGGER_DELAY = 50
}

/** Reusable spring specs */
object AnimSpring {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)
    fun <T> gentle() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
}

/**
 * Scale-down-on-press animation modifier.
 * Requires a [MutableInteractionSource] that is also passed to the clickable modifier.
 */
fun Modifier.pressAnimation(interactionSource: MutableInteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
