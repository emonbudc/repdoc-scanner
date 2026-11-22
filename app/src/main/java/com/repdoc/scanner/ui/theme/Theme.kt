package com.repdoc.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Brand = Color(0xFF00B796)
private val BrandActive = Color(0xFF00957A)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F7F3),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = BrandActive,
    onSecondary = Color.White,
    background = Color(0xFFF5F7F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFC),
    surfaceContainer = Color(0xFFF1F4F3),
    surfaceContainerHigh = Color(0xFFEAEEED),
    surfaceContainerHighest = Color(0xFFE3E8E7),
    onSurface = Color(0xFF1B1F1E),
    onSurfaceVariant = Color(0xFF6F7876),
    outline = Color(0xFFD3DAD8),
    outlineVariant = Color(0xFFE4E9E8),
    inverseSurface = Color(0xFF2C3231),
    inverseOnSurface = Color(0xFFF1F4F3),
    surfaceTint = Brand,
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3D34),
    onPrimaryContainer = Color(0xFFB9F5E8),
    secondary = BrandActive,
    onSecondary = Color.White,
    background = Color(0xFF151817),
    surface = Color(0xFF202423),
    surfaceVariant = Color(0xFF2C3130),
    surfaceContainerLowest = Color(0xFF111413),
    surfaceContainerLow = Color(0xFF1B1F1E),
    surfaceContainer = Color(0xFF202423),
    surfaceContainerHigh = Color(0xFF2A2F2E),
    surfaceContainerHighest = Color(0xFF343A38),
    onSurface = Color(0xFFF2F5F4),
    onSurfaceVariant = Color(0xFFA4ADAB),
    outline = Color(0xFF4A5351),
    outlineVariant = Color(0xFF353C3A),
    inverseSurface = Color(0xFFF1F4F3),
    inverseOnSurface = Color(0xFF2C3231),
    surfaceTint = Brand,
    scrim = Color.Black
)

private val AppTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RepdocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
