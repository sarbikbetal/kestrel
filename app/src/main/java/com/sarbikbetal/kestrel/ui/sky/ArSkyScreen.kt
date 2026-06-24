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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.SkyViewMode
import com.sarbikbetal.kestrel.ui.PollStatus
import com.sarbikbetal.kestrel.ui.SkyUiState
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

@Composable
fun ArSkyScreen(
    state: SkyUiState,
    hasCameraPermission: Boolean,
    skyViewMode: SkyViewMode,
    onToggleSkyView: () -> Unit,
    onSelectPlane: (String?) -> Unit,
    miniMap: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showCamera = skyViewMode == SkyViewMode.CAMERA && hasCameraPermission

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showCamera) {
            CameraPreview(modifier = Modifier.fillMaxSize())
            SyntheticSky(
                orientation = state.orientation,
                showStars = false,
                modifier = Modifier.fillMaxSize().alpha(0.45f),
            )
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (state.selectedHex == null) {
                UpdateTimerDonut(poll = state.poll)
            }
            val (toggleIcon, toggleDesc) = if (skyViewMode == SkyViewMode.CAMERA) {
                Icons.Filled.WbSunny to "Switch to synthetic sky"
            } else {
                Icons.Filled.CameraAlt to "Switch to camera view"
            }
            SmallFloatingActionButton(
                onClick = onToggleSkyView,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(toggleIcon, contentDescription = toggleDesc)
            }
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
            val heading = compass(state.orientation.compassBearingDeg)
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
private fun SyntheticSky(
    orientation: DeviceOrientation,
    showStars: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    // Stable star field: [azimuth 0..360°, elevation 0..80°, brightness 0..1].
    // All three axes are locked to the sky — azimuth, elevation, and roll.
    val stars = remember {
        val rng = Random(42)
        Array(150) { floatArrayOf(rng.nextFloat() * 360f, rng.nextFloat() * 80f, rng.nextFloat()) }
    }
    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        val cx = w / 2f
        val cy = h / 2f

        // horizonY: screen-space Y of the horizon centre (elevation only, no roll yet).
        val horizonY = cy + (orientation.elevationDeg / 50f) * cy

        // Roll angle: tilts the horizon line and sky/ground boundary.
        val rollRad = orientation.rollDeg * (PI / 180.0)
        val tanR = tan(rollRad).toFloat()

        // Where the tilted horizon intersects the left (x=0) and right (x=w) screen edges.
        val leftHorizonY  = horizonY - cx * tanR
        val rightHorizonY = horizonY + (w - cx) * tanR

        // Sky polygon: above the tilted horizon line.
        val skyPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, rightHorizonY)
            lineTo(0f, leftHorizonY)
            close()
        }

        // Ground polygon: below the tilted horizon line.
        val groundPath = Path().apply {
            moveTo(0f, leftHorizonY)
            lineTo(w, rightHorizonY)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        // Dark base (fills gaps when horizon is entirely off-screen)
        drawRect(Color(0xFF020810))

        // Sky: gradient from near-black zenith to blue, clipped to sky polygon
        clipPath(skyPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF020810), Color(0xFF081730), Color(0xFF183E7A)),
                    startY = 0f,
                    endY = horizonY.coerceAtLeast(1f),
                ),
                size = Size(w, h),
            )
        }

        // Ground: dark earth, clipped to ground polygon
        clipPath(groundPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1A08), Color(0xFF030803)),
                    startY = horizonY,
                    endY = h,
                ),
                size = Size(w, h),
            )
        }

        // Full 3-axis gyro-locked stars.
        // pixPerDeg matches the horizonY formula so all axes use the same angular scale.
        if (showStars) {
            val pixPerDeg = cy / 50f
            val rollCos = cos(rollRad).toFloat()
            val rollSin = sin(rollRad).toFloat()

            stars.forEach { s ->
                // Azimuth delta: positive → star is to the right of phone heading
                val dAz = ((s[0] - orientation.azimuthDeg + 540f) % 360f) - 180f
                // Raw offset from screen centre (no roll)
                val rawDx = dAz * pixPerDeg
                val rawDy = (orientation.elevationDeg - s[1]) * pixPerDeg
                // Apply roll rotation around screen centre
                val sx = cx + rawDx * rollCos - rawDy * rollSin
                val sy = cy + rawDx * rollSin + rawDy * rollCos
                // Star is visible if on-screen and above the tilted horizon at that x
                if (sx in 0f..w && sy >= 0f && sy < leftHorizonY + sx * tanR) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f + s[2] * 0.75f),
                        radius = 0.8f + s[2] * 2.2f,
                        center = Offset(sx, sy),
                    )
                }
            }
        }

        // Tilted horizon line
        drawLine(
            color = primary.copy(alpha = 0.6f),
            start = Offset(0f, leftHorizonY),
            end = Offset(w, rightHorizonY),
            strokeWidth = 2f,
        )
    }
}
