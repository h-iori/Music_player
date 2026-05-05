package com.ioristudios.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MusicDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = CoreWhite,
    primaryContainer = NeonPurpleDark,
    onPrimaryContainer = CoreWhiteDim,
    secondary = NeonPurpleLight,
    onSecondary = SurfaceDark,
    secondaryContainer = NeonPurpleSubtle,
    onSecondaryContainer = CoreWhiteDim,
    tertiary = NeonPurpleGlow,
    onTertiary = CoreWhite,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = CoreWhite,
    outline = NeonPurpleSubtle,
    outlineVariant = NeonPurpleFaint,
    inverseSurface = CoreWhiteDim,
    inverseOnSurface = SurfaceDark,
    surfaceContainerLowest = SurfaceGradientStart,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDarkElevated,
    surfaceContainerHigh = SurfaceDarkCard,
    surfaceContainerHighest = SurfaceDarkSheet,
)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    val colorScheme = MusicDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceDark.toArgb()
            window.navigationBarColor = SurfaceDark.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MusicTypography,
        shapes = MusicShapes,
        content = content
    )
}
