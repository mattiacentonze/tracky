package com.aloneagle.tracky.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightTrackyColorScheme = lightColorScheme(
    primary = TrackyGreen,
    onPrimary = TrackySurface,
    primaryContainer = TrackyMint,
    onPrimaryContainer = TrackyGreenDark,
    secondary = TrackyGreenDark,
    background = TrackyBackground,
    onBackground = TrackyInk,
    surface = TrackySurface,
    onSurface = TrackyInk,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    onSurfaceVariant = TrackyMuted,
    outline = TrackyLine,
    error = TrackyCoral,
)

private val DarkTrackyColorScheme = darkColorScheme(
    primary = ColorTokens.DarkPrimary,
    onPrimary = NightBackground,
    primaryContainer = NightSurfaceVariant,
    onPrimaryContainer = NightText,
    secondary = ColorTokens.DarkPrimary,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = ColorTokens.DarkOutline,
    error = ColorTokens.DarkError,
)

private object ColorTokens {
    val LightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0F5F3)
    val DarkPrimary = androidx.compose.ui.graphics.Color(0xFF67D5B7)
    val DarkOutline = androidx.compose.ui.graphics.Color(0xFF3D5750)
    val DarkError = androidx.compose.ui.graphics.Color(0xFFFFB4A4)
}

@Composable
fun TrackyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkTrackyColorScheme else LightTrackyColorScheme,
        content = content,
    )
}
