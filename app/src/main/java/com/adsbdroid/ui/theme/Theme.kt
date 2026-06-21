package com.adsbdroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Exported so AR HUD, map controls, and overlay tags can share one visual language.
val NightBlue = Color(0xFF0B1622)
val HudCyan = Color(0xFF35E1D2)
val HudAmber = Color(0xFFFFC857)
/** Translucent dark surface for HUD panels and FABs. */
val HudSurface = Color(0xCC0D1B2A)
/** Subtle cyan border used on HUD elements. */
val HudBorder = Color(0x5035E1D2)

// --- Solid panel surfaces shared by the flight-details card and Settings ---
/** Near-opaque panel background for full cards (details panel, settings cards). */
val PanelBg = Color(0xF20C141D)
/** Slightly lighter inset surface for rows/cells inside a panel. */
val PanelRow = Color(0xFF16222E)
/** Hairline divider/border inside panels. */
val PanelBorder = Color(0x1AFFFFFF)
/** Dimmed secondary text. */
val HudTextDim = Color(0xFF8A9AA8)

private val Ink = Color(0xFFE6EDF3)

private val DarkColors = darkColorScheme(
    primary = HudCyan,
    onPrimary = NightBlue,
    secondary = HudAmber,
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

/** Monospace typography applied app-wide for the sci-fi look. */
private val MonoTypography = Typography(
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 36.sp, lineHeight = 44.sp),
)

@Composable
fun AdsbDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MonoTypography,
        content = content,
    )
}
