package com.adsbdroid.ui.sky

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsbdroid.data.TrackSample
import com.adsbdroid.settings.Formatters
import com.adsbdroid.settings.UserPrefs
import java.util.Date
import kotlin.math.abs

private val SpeedColor = Color(0xFF4FE3D2)
private val AltColor = Color(0xFFFFC857)

/**
 * Bottom panel showing the selected aircraft's speed and altitude over time as
 * two stacked charts that share one time axis and a single draggable scrubber.
 */
@Composable
fun PlaneGraphsPanel(
    title: String,
    subtitle: String?,
    samples: List<TrackSample>,
    prefs: UserPrefs,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            when {
                samples.size >= 2 -> Charts(samples, prefs)
                loading -> CenterBox { LoadingRow() }
                error != null -> CenterBox {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                else -> CenterBox { Text("No flight history available", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Loading flight history\u2026", fontSize = 13.sp)
    }
}

@Composable
private fun Charts(samples: List<TrackSample>, prefs: UserPrefs) {
    val tMin = samples.first().timeMs
    val tMax = samples.last().timeMs
    val span = (tMax - tMin).coerceAtLeast(1L)
    val xFracs = remember(samples) { samples.map { (it.timeMs - tMin).toFloat() / span } }

    var scrub by remember(samples) { mutableStateOf<Float?>(null) }
    val selectedIndex = remember(scrub, xFracs) {
        val f = scrub ?: return@remember samples.lastIndex
        xFracs.indices.minByOrNull { abs(xFracs[it] - f) } ?: samples.lastIndex
    }
    val sel = samples[selectedIndex]

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Readout("Speed", Formatters.speed(sel.speedKnots, prefs.speedUnit), SpeedColor)
            Readout(
                "Altitude",
                sel.altitudeMeters?.let { Formatters.altitude(it, prefs.altitudeUnit) } ?: "--",
                AltColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Formatters.clock(Date(sel.timeMs), prefs.timeFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(samples) {
                    detectTapGestures { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) }
                }
                .pointerInput(samples) {
                    detectDragGestures { change, _ ->
                        scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    }
                },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChart(
                    xFracs = xFracs,
                    values = samples.map { it.speedKnots },
                    color = SpeedColor,
                    scrub = scrub,
                    label = { Formatters.speed(it, prefs.speedUnit) },
                )
                MetricChart(
                    xFracs = xFracs,
                    values = samples.map { it.altitudeMeters },
                    color = AltColor,
                    scrub = scrub,
                    label = { Formatters.altitude(it, prefs.altitudeUnit) },
                )
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricChart(
    xFracs: List<Float>,
    values: List<Double?>,
    color: Color,
    scrub: Float?,
    label: (Double) -> String,
) {
    val present = values.withIndex().filter { it.value != null }
    val nums = present.map { it.value!! }
    val minV = nums.minOrNull() ?: 0.0
    val maxV = nums.maxOrNull() ?: 1.0
    val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0

    val labelPaint = remember(color) {
        Paint().apply {
            isAntiAlias = true
            this.color = 0xFFB9C6D2.toInt()
            textSize = 24f
        }
    }
    val gridColor = Color.White.copy(alpha = 0.10f)

    Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
        val w = size.width
        val h = size.height
        val padL = 12f
        val padR = 56f // room for value labels on the right
        val padV = 10f
        val plotW = w - padL - padR
        val plotH = h - padV * 2

        fun px(frac: Float) = padL + frac * plotW
        fun py(value: Double) = padV + (1f - ((value - minV) / range).toFloat()) * plotH

        // Horizontal gridlines + min/max labels.
        listOf(0f to maxV, 1f to minV).forEach { (t, v) ->
            val y = padV + t * plotH
            drawLine(gridColor, Offset(padL, y), Offset(padL + plotW, y), 1f)
            drawContext.canvas.nativeCanvas.drawText(label(v), padL + plotW + 6f, y + 8f, labelPaint)
        }

        // Build the line across present points.
        val path = Path()
        present.forEachIndexed { i, (origIdx, value) ->
            val x = px(xFracs[origIdx])
            val y = py(value!!)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5f))

        // Scrubber: vertical line + dot at the nearest present point.
        if (scrub != null) {
            val x = px(scrub)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, padV), Offset(x, padV + plotH), 1.5f)
            val nearest = present.minByOrNull { abs(px(xFracs[it.index]) - x) }
            nearest?.let {
                drawCircle(color, radius = 4.5f, center = Offset(px(xFracs[it.index]), py(it.value!!)))
            }
        }
    }
}
