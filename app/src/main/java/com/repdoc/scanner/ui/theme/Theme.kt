package com.repdoc.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF4F5B6D),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF3),
    onSurface = Color(0xFF171A1F),
    onSurfaceVariant = Color(0xFF5B626E),
    outline = Color(0xFFC5CBD5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF00458F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB9C5D8),
    background = Color(0xFF111418),
    surface = Color(0xFF181C22),
    surfaceVariant = Color(0xFF252A32),
    onSurface = Color(0xFFE5E8EE),
    onSurfaceVariant = Color(0xFFB8C0CC),
    outline = Color(0xFF454C57)
)

@Composable
fun RepdocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
