package com.adsbdroid.ui.sky

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsbdroid.sensors.DeviceOrientation
import com.adsbdroid.settings.Formatters
import com.adsbdroid.ui.PollStatus
import com.adsbdroid.ui.SkyUiState
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ArSkyScreen(
    state: SkyUiState,
    hasCameraPermission: Boolean,
    onOpenSettings: () -> Unit,
    onSelectPlane: (String?) -> Unit,
    miniMap: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B1622))) {
        if (hasCameraPermission) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        } else {
            SyntheticSky(orientation = state.orientation, modifier = Modifier.fillMaxSize())
        }

        // Dark scrim so overlay markers stay legible over the camera feed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.2f))
                    )
                )
        )

        SkyOverlay(
            planes = state.planes,
            orientation = state.orientation,
            prefs = state.prefs,
            selectedHex = state.selectedHex,
            onSelect = onSelectPlane,
            modifier = Modifier.fillMaxSize(),
        )

        StatusBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }

        if (state.selectedHex == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                miniMap()
            }

            UpdateTimerDonut(
                poll = state.poll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            )
        } else {
            val selected = state.planes.firstOrNull { it.aircraft.hex == state.selectedHex }
            val flag = selected?.country?.flag?.let { "$it " } ?: ""
            val title = flag + (selected?.aircraft?.callsign ?: state.selectedHex)
            val subtitle = listOfNotNull(
                selected?.aircraft?.type,
                selected?.aircraft?.registration,
            ).joinToString("  \u00B7  ").ifEmpty { null }

            PlaneGraphsPanel(
                title = title,
                subtitle = subtitle,
                samples = state.history,
                prefs = state.prefs,
                loading = state.historyLoading,
                error = state.historyError,
                onClose = { onSelectPlane(null) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * A 36dp donut that fills over the current API polling period. Green when the
 * last update succeeded, red when it failed.
 */
@Composable
private fun UpdateTimerDonut(poll: PollStatus, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(poll.updatedAtMs, poll.periodMs) {
        if (poll.periodMs <= 0L) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // The state may reach us a little after the update; resume mid-cycle.
        val elapsed = (SystemClock.elapsedRealtime() - poll.updatedAtMs).coerceAtLeast(0L)
        val remaining = (poll.periodMs - elapsed).coerceAtLeast(0L)
        progress.snapTo((elapsed.toFloat() / poll.periodMs).coerceIn(0f, 1f))
        if (remaining > 0L) {
            progress.animateTo(1f, animationSpec = tween(remaining.toInt(), easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }

    val arcColor = if (poll.ok) Color(0xFF4CAF50) else Color(0xFFE53935)
    val trackColor = Color.White.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun StatusBar(state: SkyUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.4f),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val heading = compass(state.orientation.azimuthDeg)
            val clock = Formatters.clock(Date(), state.prefs.timeFormat)
            Text(
                text = "$clock   |   $heading   |   ${state.planes.size} planes",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            val loc = state.location
            Text(
                text = when {
                    loc == null -> "Acquiring GPS\u2026"
                    else -> "%.4f, %.4f".format(loc.lat, loc.lon)
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (!state.sensorAvailable) {
                Text("No rotation sensor on this device", color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
            state.error?.let {
                Text(it, color = Color(0xFFFFC857), fontSize = 12.sp)
            }
        }
    }
}

private fun compass(az: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((az + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
    return "${az.roundToInt()}\u00B0 ${dirs[idx]}"
}

/** Fallback background: a rendered sky/horizon that reacts to device pitch. */
@Composable
private fun SyntheticSky(orientation: DeviceOrientation, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF06101A),
                0.6f to Color(0xFF12283D),
                1f to Color(0xFF1E3A52),
            )
        )
        // Horizon shifts down as you tilt up toward the sky.
        val horizonY = (h / 2f) + (orientation.elevationDeg / 50f) * (h / 2f)
        if (horizonY in 0f..h) {
            drawLine(
                color = Color(0xFF35E1D2).copy(alpha = 0.5f),
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 2f,
            )
        }
    }
}
