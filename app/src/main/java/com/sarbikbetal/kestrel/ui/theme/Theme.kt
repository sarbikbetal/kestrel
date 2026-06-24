package com.sarbikbetal.kestrel.ui.theme

import android.os.Build
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.settings.AppTheme

// ── Astro Red ────────────────────────────────────────────────────────────────
// OLED black background, deep astronomy red primaries, amber-orange accents.
// No whites, blues, or greens — optimised for night-sky viewing.
private val AstroRedScheme = darkColorScheme(
    primary                 = Color(0xFFCC1100),
    onPrimary               = Color(0xFF1A0000),
    primaryContainer        = Color(0xFF3D0000),
    onPrimaryContainer      = Color(0xFFFF9999),
    secondary               = Color(0xFFFF5500),   // amber/dark-orange accent
    onSecondary             = Color(0xFF1A0A00),
    secondaryContainer      = Color(0xFF3D1500),
    onSecondaryContainer    = Color(0xFFFFD0A0),
    tertiary                = Color(0xFFFF8800),
    onTertiary              = Color(0xFF1A0800),
    tertiaryContainer       = Color(0xFF3D1800),
    onTertiaryContainer     = Color(0xFFFFE0B0),
    error                   = Color(0xFFFF5500),
    onError                 = Color(0xFF1A0000),
    errorContainer          = Color(0xFF3D0000),
    onErrorContainer        = Color(0xFFFF9999),
    background              = Color(0xFF000000),
    onBackground            = Color(0xFFCC1100),
    surface                 = Color(0xFF000000),
    onSurface               = Color(0xFFEE3300),
    surfaceVariant          = Color(0xFF1A0000),
    onSurfaceVariant        = Color(0xFFAA2200),
    surfaceContainerLowest  = Color(0xFF000000),
    surfaceContainerLow     = Color(0xFF0D0000),
    surfaceContainer        = Color(0xFF180000),
    surfaceContainerHigh    = Color(0xFF220000),
    surfaceContainerHighest = Color(0xFF2E0000),
    outline                 = Color(0xFF661100),
    outlineVariant          = Color(0xFF3D1100),
    inverseSurface          = Color(0xFFFFCDD2),
    inverseOnSurface        = Color(0xFF3D0000),
    inversePrimary          = Color(0xFFCC1100),
    scrim                   = Color(0xFF000000),
)

// ── Avionics Green ────────────────────────────────────────────────────────────
// Dark charcoal background, phosphor-green primaries, amber-yellow accents.
// Retro CRT cockpit HUD aesthetic with monospaced typography and sharp corners.
private val AvionicsGreenScheme = darkColorScheme(
    primary                 = Color(0xFF39FF14),   // phosphor green
    onPrimary               = Color(0xFF001200),
    primaryContainer        = Color(0xFF003A00),
    onPrimaryContainer      = Color(0xFF80FF60),
    secondary               = Color(0xFFFFCC00),   // high-voltage amber/yellow
    onSecondary             = Color(0xFF1A0F00),
    secondaryContainer      = Color(0xFF3D2800),
    onSecondaryContainer    = Color(0xFFFFE680),
    tertiary                = Color(0xFF00FF66),
    onTertiary              = Color(0xFF001A0D),
    tertiaryContainer       = Color(0xFF003D1A),
    onTertiaryContainer     = Color(0xFF80FFB0),
    error                   = Color(0xFFFFCC00),
    onError                 = Color(0xFF1A0F00),
    errorContainer          = Color(0xFF3D2800),
    onErrorContainer        = Color(0xFFFFE680),
    background              = Color(0xFF080D08),
    onBackground            = Color(0xFF39FF14),
    surface                 = Color(0xFF080D08),
    onSurface               = Color(0xFF39FF14),
    surfaceVariant          = Color(0xFF121812),
    onSurfaceVariant        = Color(0xFF20CC00),
    surfaceContainerLowest  = Color(0xFF040704),
    surfaceContainerLow     = Color(0xFF080D08),
    surfaceContainer        = Color(0xFF0E160E),
    surfaceContainerHigh    = Color(0xFF142014),
    surfaceContainerHighest = Color(0xFF1A2A1A),
    outline                 = Color(0xFF2A5A2A),
    outlineVariant          = Color(0xFF162616),
    inverseSurface          = Color(0xFF80FF60),
    inverseOnSurface        = Color(0xFF003A00),
    inversePrimary          = Color(0xFF006600),
    scrim                   = Color(0xFF000000),
)

// Monospaced typography for Avionics Green only — maps to the system monospace
// font (Droid Mono / Roboto Mono) for a flight-computer feel.
// All other themes use the default Typography() so spacing and proportions
// remain consistent when the user switches between them.
private val MonoTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.Monospace),
    displayMedium  = TextStyle(fontFamily = FontFamily.Monospace),
    displaySmall   = TextStyle(fontFamily = FontFamily.Monospace),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Monospace),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Monospace),
    titleLarge     = TextStyle(fontFamily = FontFamily.Monospace),
    titleMedium    = TextStyle(fontFamily = FontFamily.Monospace),
    titleSmall     = TextStyle(fontFamily = FontFamily.Monospace),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Monospace),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Monospace),
    bodySmall      = TextStyle(fontFamily = FontFamily.Monospace),
    labelLarge     = TextStyle(fontFamily = FontFamily.Monospace),
    labelMedium    = TextStyle(fontFamily = FontFamily.Monospace),
    labelSmall     = TextStyle(fontFamily = FontFamily.Monospace),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveShapes = Shapes(
    extraSmall          = RoundedCornerShape(4.dp),
    small               = RoundedCornerShape(8.dp),
    medium              = RoundedCornerShape(16.dp),
    large               = RoundedCornerShape(24.dp),
    extraLarge          = RoundedCornerShape(32.dp),
    largeIncreased      = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(48.dp),
)

// Sharp 90-degree corners for the CRT instrument aesthetic.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SharpShapes = Shapes(
    extraSmall          = RoundedCornerShape(0.dp),
    small               = RoundedCornerShape(0.dp),
    medium              = RoundedCornerShape(2.dp),
    large               = RoundedCornerShape(2.dp),
    extraLarge          = RoundedCornerShape(2.dp),
    largeIncreased      = RoundedCornerShape(2.dp),
    extraLargeIncreased = RoundedCornerShape(2.dp),
)

// Exposes the active AppTheme to any composable in the tree without threading it
// through every parameter chain.
val LocalAppTheme = staticCompositionLocalOf { AppTheme.MATERIAL_YOU }

// Desaturate → invert → keep only the red channel.
// Result: dark base map with bright-red roads/labels — classic astronomical red-light mode.
val ASTRO_RED_MAP_FILTER: ColorMatrixColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -0.299f, -0.587f, -0.114f, 0f, 255f,  // R = 255 - luma
             0f,      0f,      0f,     0f,   0f,  // G = 0
             0f,      0f,      0f,     0f,   0f,  // B = 0
             0f,      0f,      0f,     1f,   0f,  // A unchanged
        )
    )
)

private val ScanlineColor = Color.Black.copy(alpha = 0.22f)

// CRT scanline overlay — one dark band every 6px, 3px wide.
// Wider gap + thicker stroke makes the raster pattern visually distinct.
// ponytail: Offset is a value class (no heap allocation). Drawn once per layout
// pass, not per sensor frame, so negligible perf cost.
fun Modifier.avionicsScanlines(): Modifier = drawWithContent {
    drawContent()
    val step = 6f
    var y = 0f
    while (y < size.height) {
        drawLine(
            color       = ScanlineColor,
            start       = Offset(0f, y),
            end         = Offset(size.width, y),
            strokeWidth = 3f,
        )
        y += step
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KestrelTheme(
    appTheme: AppTheme = AppTheme.MATERIAL_YOU,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()

    val colorScheme: ColorScheme = when (appTheme) {
        AppTheme.MATERIAL_YOU -> {
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) darkColorScheme() else lightColorScheme()
            }
        }
        AppTheme.ASTRO_RED      -> AstroRedScheme
        AppTheme.AVIONICS_GREEN -> AvionicsGreenScheme
    }

    // Only Avionics Green changes the typeface — shapes and typography are
    // identical between Material You and Astro Red so layout stays stable.
    val typography = if (appTheme == AppTheme.AVIONICS_GREEN) MonoTypography else Typography()
    val shapes     = if (appTheme == AppTheme.AVIONICS_GREEN) SharpShapes    else ExpressiveShapes

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialExpressiveTheme(
            colorScheme  = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes       = shapes,
            typography   = typography,
            content      = content,
        )
    }
}
