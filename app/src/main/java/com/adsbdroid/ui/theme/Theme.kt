package com.adsbdroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightBlue = Color(0xFF0B1622)
private val Cyan = Color(0xFF35E1D2)
private val Amber = Color(0xFFFFC857)
private val Ink = Color(0xFFE6EDF3)

// A single, coherent dark Material 3 scheme used across every page so the
// camera AR view, maps, and settings share one look.
private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = NightBlue,
    secondary = Amber,
    onSecondary = NightBlue,
    background = NightBlue,
    onBackground = Ink,
    surface = Color(0xFF11202F),
    onSurface = Ink,
    surfaceVariant = Color(0xFF1B2C3D),
    onSurfaceVariant = Color(0xFFB9C6D2),
    error = Color(0xFFFF6B6B),
    onError = NightBlue,
)

@Composable
fun AdsbDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
