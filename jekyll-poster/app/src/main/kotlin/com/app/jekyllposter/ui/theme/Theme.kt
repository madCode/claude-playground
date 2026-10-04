package com.app.jekyllposter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB5341C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3E0500),
    secondary = Color(0xFF6F5A53),
    secondaryContainer = Color(0xFFF8DDD5),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceContainer = Color(0xFFF6EFEA),
    surfaceContainerHigh = Color(0xFFF0E8E3),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A5),
    onPrimary = Color(0xFF631000),
    primaryContainer = Color(0xFF8C1F0A),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE7BDB3),
    secondaryContainer = Color(0xFF574238),
    background = Color(0xFF1A1110),
    surface = Color(0xFF1A1110),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322826),
)

@Composable
fun PosterTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
