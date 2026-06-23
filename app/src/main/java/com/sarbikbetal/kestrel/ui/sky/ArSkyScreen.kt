package com.sarbikbetal.kestrel.ui.sky

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.ui.PollStatus
import com.sarbikbetal.kestrel.ui.SkyUiState
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ArSkyScreen(
    state: SkyUiState,
    hasCameraPermission: Boolean,
    onSelectPlane: (String?) -> Unit,
    miniMap: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (hasCameraPermission) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        } else {
            SyntheticSky(orientation = state.orientation, modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.12f)),
                    ),
                ),
        )

        SkyOverlay(
            planes = state.planes,
            orientation = state.orientation,
            prefs = state.prefs,
            selectedHex = state.selectedHex,
            onSelect = onSelectPlane,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusCard(state = state)

            if (state.locationIsStale) {
                StaleLocationChip()
            }
        }

        if (state.selectedHex == null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.largeIncreased,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
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
        }
    }
}

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

    val arcColor = if (poll.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
private fun StatusCard(state: SkyUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val heading = compass(state.orientation.azimuthDeg)
            val clock = Formatters.clock(Date(), state.prefs.timeFormat)
            Text(
                text = "$clock · $heading · ${state.planes.size} planes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            val loc = state.location
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (locText, locColor) = when {
                    loc == null -> "Acquiring GPS…" to MaterialTheme.colorScheme.onSurfaceVariant
                    state.locationIsStale -> "Last known %.4f, %.4f".format(loc.lat, loc.lon) to
                        MaterialTheme.colorScheme.tertiary
                    else -> "%.4f, %.4f".format(loc.lat, loc.lon) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (loc != null && !state.locationIsStale) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = locText,
                    style = MaterialTheme.typography.bodySmall,
                    color = locColor,
                )
            }

            if (!state.sensorAvailable) {
                Text(
                    "No rotation sensor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StaleLocationChip() {
    val ctx = LocalContext.current
    AssistChip(
        onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        label = {
            Text(
                "Last known location — tap to enable GPS",
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.GpsOff,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        border = null,
    )
}

private fun compass(az: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((az + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
    return "${az.roundToInt()}° ${dirs[idx]}"
}

@Composable
private fun SyntheticSky(orientation: DeviceOrientation, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF06101A),
                0.6f to Color(0xFF12283D),
                1f to Color(0xFF1E3A52),
            ),
        )
        val horizonY = (h / 2f) + (orientation.elevationDeg / 50f) * (h / 2f)
        if (horizonY in 0f..h) {
            drawLine(
                color = primary.copy(alpha = 0.5f),
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 2f,
            )
        }
    }
}
