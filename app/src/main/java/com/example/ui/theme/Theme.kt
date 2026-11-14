package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GemCyan,
    onPrimary = Color.Black,
    primaryContainer = GemViolet.copy(alpha = 0.3f),
    onPrimaryContainer = GemCyanVariant,
    secondary = GemViolet,
    onSecondary = Color.White,
    secondaryContainer = GemDarkCard,
    onSecondaryContainer = GemPurple,
    tertiary = GemElectricBlue,
    background = GemDeepNavy,
    onBackground = TextPrimaryLight,
    surface = GemDarkSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = GemDarkCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = GemCardBorder
)

private val LightColorScheme = darkColorScheme( // Keep VoiceGem in dark futuristic palette for authentic glowing gem aesthetic
    primary = GemCyan,
    onPrimary = Color.Black,
    secondary = GemViolet,
    onSecondary = Color.White,
    background = GemDeepNavy,
    onBackground = TextPrimaryLight,
    surface = GemDarkSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = GemDarkCard,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our tailored VoiceGem color scheme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
