package com.sarbikbetal.kestrel.ui.sky

import android.content.Intent
import android.graphics.Paint
import android.widget.Toast
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.AppTheme
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.SkyViewMode
import com.sarbikbetal.kestrel.ui.theme.LocalAppTheme
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
    orientation: State<DeviceOrientation>,
    hasCameraPermission: Boolean,
    skyViewMode: SkyViewMode,
    onToggleSkyView: () -> Unit,
    onSelectPlane: (String?) -> Unit,
    miniMap: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showCamera = skyViewMode == SkyViewMode.CAMERA && hasCameraPermission

    val ctx = LocalContext.current
    val openLocationSettings = {
        ctx.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    // One-off toast when we fall back to a cached fix; the persistent GPS FAB
    // stays visible until a live fix arrives as the lasting affordance.
    LaunchedEffect(state.locationIsStale) {
        if (!state.locationIsStale) return@LaunchedEffect
        Toast.makeText(
            ctx,
            "Using last known location — tap the GPS icon to enable location services",
            Toast.LENGTH_LONG,
        ).show()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showCamera) {
            CameraPreview(modifier = Modifier.fillMaxSize())
            SyntheticSky(
                orientation = orientation,
                showStars = false,
                modifier = Modifier.fillMaxSize().alpha(0.45f),
            )
        } else {
            SyntheticSky(orientation = orientation, modifier = Modifier.fillMaxSize())
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
            orientation = orientation,
            prefs = state.prefs,
            selectedHex = state.selectedHex,
            onSelect = onSelectPlane,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HudCompass(state = state, orientation = orientation)

            if (state.selectedHex == null) {
                UpdateTimerDonut(
                    poll = state.poll,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp, end = 16.dp),
                )
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
            if (state.locationIsStale) {
                SmallFloatingActionButton(
                    onClick = openLocationSettings,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = FloatingActionButtonDefaults.smallShape,
                ) {
                    Icon(Icons.Filled.GpsOff, contentDescription = "Location not enabled — tap to turn on")
                }
            }
            val (toggleIcon, toggleDesc) = if (skyViewMode == SkyViewMode.CAMERA) {
                Icons.Filled.AutoAwesome to "Switch to synthetic sky"
            } else {
                Icons.Filled.Videocam to "Switch to live camera view"
            }
            FloatingActionButton(
                onClick = onToggleSkyView,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = FloatingActionButtonDefaults.shape,
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

private fun cardinalLabel(deg: Int): String = when (deg) {
    0 -> "N"; 45 -> "NE"; 90 -> "E"; 135 -> "SE"
    180 -> "S"; 225 -> "SW"; 270 -> "W"; 315 -> "NW"
    else -> ""
}

@Composable
private fun HudCompass(
    state: SkyUiState,
    orientation: State<DeviceOrientation>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val timeStr   = Formatters.clock(Date(), state.prefs.timeFormat)
    val countStr  = "${state.planes.size} ✈"
    // Theme-aware scrim: dark in dark mode, light in light mode (text uses onSurface).
    val scoopColor = cs.surface.copy(alpha = 0.72f)

    // All Paint objects remembered — zero per-frame allocation in the draw block.
    val primaryArgb   = cs.primary.toArgb()
    val dimArgb       = cs.onSurface.copy(alpha = 0.45f).toArgb()
    val onSurfArgb    = cs.onSurface.toArgb()

    val cardinalPaint = remember(primaryArgb) { Paint().apply {
        isAntiAlias = true; color = primaryArgb
        textSize = 38f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }}
    val northPaint = remember { Paint().apply {
        isAntiAlias = true; color = android.graphics.Color.RED
        textSize = 38f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }}
    val degPaint = remember(dimArgb) { Paint().apply {
        isAntiAlias = true; color = dimArgb
        textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT
    }}
    val majorPaint = remember(primaryArgb) { Paint().apply {
        isAntiAlias = true; color = primaryArgb; strokeWidth = 2.5f; style = Paint.Style.STROKE
    }}
    val minorPaint = remember(dimArgb) { Paint().apply {
        isAntiAlias = true; color = dimArgb; strokeWidth = 1.5f; style = Paint.Style.STROKE
    }}
    val infoLPaint = remember(onSurfArgb) { Paint().apply {
        isAntiAlias = true; color = onSurfArgb
        textSize = 30f; textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD
    }}
    val infoRPaint = remember(onSurfArgb) { Paint().apply {
        isAntiAlias = true; color = onSurfArgb
        textSize = 30f; textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD
    }}

    Column(modifier = modifier) {
        // Single canvas: background shape + compass tape + info text.
        Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
            // Read orientation in the draw phase so a heading change repaints the tape
            // without recomposing HudCompass.
            val bearing = orientation.value.compassBearingDeg
            val w     = size.width
            val h     = size.height
            val cx    = w / 2f
            val tapeH = 58.dp.toPx()
            val infoH = h - tapeH
            val nc    = drawContext.canvas.nativeCanvas

            // ── Scooped background ────────────────────────────────────────
            // Centre is cut away below the tape; left/right wings hold the info text.
            // The cutout widens slightly toward the bottom (trapezoidal notch).
            val scoopTopHW = w * 0.20f   // half-width at the tape/info boundary
            val scoopBotHW = w * 0.32f   // half-width at the bottom edge
            val bgPath = Path().apply {
                moveTo(0f, 0f);               lineTo(w, 0f)
                lineTo(w, h);                 lineTo(cx + scoopBotHW, h)
                lineTo(cx + scoopTopHW, tapeH)
                lineTo(cx - scoopTopHW, tapeH)
                lineTo(cx - scoopBotHW, h);   lineTo(0f, h)
                close()
            }
            drawPath(bgPath, scoopColor)

            // ── Compass tape ──────────────────────────────────────────────
            val pxPerDeg = w / 80f
            val shortH   = 8.dp.toPx()
            val majorH   = 14.dp.toPx()
            val cardH    = 20.dp.toPx()
            val textY    = tapeH - 6.dp.toPx()

            val start = (bearing - 42f).toInt() - 1
            val end   = (bearing + 42f).toInt() + 1
            for (raw in start..end) {
                val norm = ((raw % 360) + 360) % 360
                val x = cx + (raw.toFloat() - bearing) * pxPerDeg
                if (x < -32f || x > w + 32f) continue
                when {
                    norm % 45 == 0 -> {
                        nc.drawLine(x, 0f, x, cardH, majorPaint)
                        val lbl = cardinalLabel(norm)
                        nc.drawText(lbl, x, textY, if (norm == 0) northPaint else cardinalPaint)
                    }
                    norm % 15 == 0 -> {
                        nc.drawLine(x, 0f, x, majorH, majorPaint)
                        nc.drawText("$norm", x, textY - 2.dp.toPx(), degPaint)
                    }
                    norm % 5 == 0 -> nc.drawLine(x, 0f, x, shortH, minorPaint)
                }
            }

            // Centre pointer — taller than cardinal ticks, marks current heading
            drawLine(cs.primary, Offset(cx, 0f), Offset(cx, cardH + 6.dp.toPx()), 3f)

            // ── Info text in wings ────────────────────────────────────────
            val infoBaseline = tapeH + infoH * 0.68f
            val padX = 14.dp.toPx()
            nc.drawText(timeStr,  padX,     infoBaseline, infoLPaint)
            nc.drawText(countStr, w - padX, infoBaseline, infoRPaint)
        }

        // Error banner — only rendered when needed, outside the canvas.
        if (!state.sensorAvailable || state.error != null) {
            Text(
                if (!state.sensorAvailable) "No rotation sensor" else state.error ?: "",
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = cs.error,
            )
        }
    }
}

@Composable
private fun SyntheticSky(
    orientation: State<DeviceOrientation>,
    showStars: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val astroRed  = LocalAppTheme.current == AppTheme.ASTRO_RED

    // Stable star field: [azimuth 0..360°, elevation 0..80°, brightness 0..1].
    // All three axes are locked to the sky — azimuth, elevation, and roll.
    val stars = remember {
        val rng = Random(42)
        Array(150) { floatArrayOf(rng.nextFloat() * 360f, rng.nextFloat() * 80f, rng.nextFloat()) }
    }

    // Theme-keyed colours resolved once per composition, not per draw frame.
    val baseColor    = if (astroRed) Color(0xFF000000) else Color(0xFF020810)
    val skyColors    = if (astroRed) listOf(Color(0xFF000000), Color(0xFF0A0000), Color(0xFF180000))
                       else          listOf(Color(0xFF020810), Color(0xFF081730), Color(0xFF183E7A))
    val groundColors = if (astroRed) listOf(Color(0xFF050000), Color(0xFF000000))
                       else          listOf(Color(0xFF0A1A08), Color(0xFF030803))
    // Astro Red: dim deep-crimson pinpoints. Normal: white star points.
    val starTint     = if (astroRed) Color(0xFFCC1100) else Color.White

    Canvas(modifier = modifier) {
        // Read orientation in the draw phase: phone motion repaints the sky without
        // recomposing this composable.
        val o  = orientation.value
        val h  = size.height
        val w  = size.width
        val cx = w / 2f
        val cy = h / 2f

        // horizonY: screen-space Y of the horizon centre (elevation only, no roll yet).
        val horizonY = cy + (o.elevationDeg / 50f) * cy

        // Roll angle: tilts the horizon line and sky/ground boundary.
        val rollRad = o.rollDeg * (PI / 180.0)
        val tanR    = tan(rollRad).toFloat()

        // Where the tilted horizon intersects the left (x=0) and right (x=w) screen edges.
        val leftHorizonY  = horizonY - cx * tanR
        val rightHorizonY = horizonY + (w - cx) * tanR

        // Sky polygon: above the tilted horizon line.
        val skyPath = Path().apply {
            moveTo(0f, 0f);         lineTo(w, 0f)
            lineTo(w, rightHorizonY); lineTo(0f, leftHorizonY)
            close()
        }

        // Ground polygon: below the tilted horizon line.
        val groundPath = Path().apply {
            moveTo(0f, leftHorizonY); lineTo(w, rightHorizonY)
            lineTo(w, h);             lineTo(0f, h)
            close()
        }

        // Dark base (fills gaps when horizon is entirely off-screen)
        drawRect(baseColor)

        // Sky: gradient from near-black zenith down to horizon colour
        clipPath(skyPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = skyColors,
                    startY = 0f,
                    endY   = horizonY.coerceAtLeast(1f),
                ),
                size = Size(w, h),
            )
        }

        // Ground: dark earth, clipped to ground polygon
        clipPath(groundPath) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = groundColors,
                    startY = horizonY,
                    endY   = h,
                ),
                size = Size(w, h),
            )
        }

        // Full 3-axis gyro-locked stars.
        // pixPerDeg matches the horizonY formula so all axes use the same angular scale.
        if (showStars) {
            val pixPerDeg = cy / 50f
            val rollCos   = cos(rollRad).toFloat()
            val rollSin   = sin(rollRad).toFloat()

            stars.forEach { s ->
                // Azimuth delta uses compassBearingDeg so stars and planes share the same
                // true-north reference frame and don't drift relative to each other.
                val dAz   = ((s[0] - o.compassBearingDeg + 540f) % 360f) - 180f
                val rawDx = dAz * pixPerDeg
                val rawDy = (o.elevationDeg - s[1]) * pixPerDeg
                val sx    = cx + rawDx * rollCos - rawDy * rollSin
                val sy    = cy + rawDx * rollSin + rawDy * rollCos
                if (sx in 0f..w && sy >= 0f && sy < leftHorizonY + sx * tanR) {
                    drawCircle(
                        color  = starTint.copy(alpha = 0.25f + s[2] * 0.75f),
                        radius = 0.8f + s[2] * 2.2f,
                        center = Offset(sx, sy),
                    )
                }
            }
        }

        // Tilted horizon line — uses theme primary (already red in Astro Red)
        drawLine(
            color       = primary.copy(alpha = 0.6f),
            start       = Offset(0f, leftHorizonY),
            end         = Offset(w, rightHorizonY),
            strokeWidth = 2f,
        )
    }
}
