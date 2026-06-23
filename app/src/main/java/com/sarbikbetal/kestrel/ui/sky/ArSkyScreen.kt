package com.sarbikbetal.kestrel.ui.sky

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.ui.PollStatus
import com.sarbikbetal.kestrel.ui.SkyUiState
import com.sarbikbetal.kestrel.ui.theme.HudAmber
import com.sarbikbetal.kestrel.ui.theme.HudBorder
import com.sarbikbetal.kestrel.ui.theme.HudCyan
import com.sarbikbetal.kestrel.ui.theme.HudSurface
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

        // Top bar: status pill + settings button in a single Row so they never overlap.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBar(state = state, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
                    .background(HudSurface),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = HudCyan)
            }
        }

        // Stale-location nudge: shown when we're using a cached fix, not live GPS.
        if (state.locationIsStale) {
            StaleLocationBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 72.dp, start = 12.dp, end = 12.dp),
            )
        }

        if (state.selectedHex == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, HudBorder, RoundedCornerShape(14.dp)),
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

            PlaneGraphsPanel(
                selected = selected,
                fallbackTitle = state.selectedHex ?: "",
                details = state.details,
                detailsLoading = state.detailsLoading,
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
internal fun UpdateTimerDonut(poll: PollStatus, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(poll.updatedAtMs, poll.periodMs) {
        if (poll.periodMs <= 0L) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
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
            .border(1.dp, HudBorder, CircleShape)
            .background(HudSurface),
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
        modifier = modifier.border(1.dp, HudBorder, RoundedCornerShape(12.dp)),
        color = HudSurface,
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val heading = compass(state.orientation.azimuthDeg)
            val clock = Formatters.clock(Date(), state.prefs.timeFormat)
            Text(
                text = "$clock   |   $heading   |   ${state.planes.size} PLANES",
                color = HudCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            val loc = state.location
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (locText, locColor) = when {
                    loc == null -> "ACQUIRING GPS\u2026" to Color.White.copy(alpha = 0.45f)
                    state.locationIsStale -> "LAST KNOWN  %.4f, %.4f".format(loc.lat, loc.lon) to HudAmber.copy(alpha = 0.8f)
                    else -> "%.4f, %.4f".format(loc.lat, loc.lon) to Color.White.copy(alpha = 0.65f)
                }
                if (loc != null && !state.locationIsStale) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = locText,
                    color = locColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (!state.sensorAvailable) {
                Text("NO ROTATION SENSOR", color = Color(0xFFFF6B6B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            state.error?.let {
                Text(it.uppercase(), color = HudAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun StaleLocationBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, HudAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .background(Color(0xCC1A1200))
            .clickable {
                ctx.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.GpsOff,
            contentDescription = null,
            tint = HudAmber,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "LAST KNOWN LOCATION — TAP TO ENABLE GPS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = HudAmber,
        )
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
