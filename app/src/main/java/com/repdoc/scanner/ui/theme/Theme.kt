package com.repdoc.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF00B796)
private val BrandActive = Color(0xFF00957A)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F7F3),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = BrandActive,
    onSecondary = Color.White,
    background = Color(0xFFF7F7F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F1),
    onSurface = Color(0xFF1E2939),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color(0xFFDCDCDC)
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3D34),
    onPrimaryContainer = Color(0xFFB9F5E8),
    secondary = BrandActive,
    onSecondary = Color.White,
    background = Color(0xFF1C1C1E),
    surface = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF9C9C9C),
    outline = Color(0xFF48484A)
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
