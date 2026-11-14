package com.dualframe.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DualFrameDarkColorScheme = darkColorScheme(
    primary = FrameGold,
    onPrimary = FrameBackground,
    secondary = FrameGoldDark,
    onSecondary = FrameBackground,
    background = FrameBackground,
    onBackground = FrameText,
    surface = FrameSurface,
    onSurface = FrameText,
    surfaceVariant = FrameSurfaceVariant,
    onSurfaceVariant = FrameMutedText,
    outline = FrameMutedText
)

@Composable
fun DualFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DualFrameDarkColorScheme,
        content = content
    )
}
