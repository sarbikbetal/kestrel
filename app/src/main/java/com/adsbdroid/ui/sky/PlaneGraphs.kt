package com.adsbdroid.ui.sky

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsbdroid.data.FlightDetails
import com.adsbdroid.data.TrackSample
import com.adsbdroid.settings.Formatters
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.theme.HudAmber
import com.adsbdroid.ui.theme.HudBorder
import com.adsbdroid.ui.theme.HudCyan
import com.adsbdroid.ui.theme.HudSurface
import java.util.Date
import kotlin.math.abs

private val SpeedColor = Color(0xFF4FE3D2)
private val AltColor = Color(0xFFFFC857)

/**
 * Bottom panel showing enriched flight details (airline, route) + speed/altitude
 * charts for the selected aircraft.
 */
@Composable
fun PlaneGraphsPanel(
    title: String,
    subtitle: String?,
    details: FlightDetails?,
    detailsLoading: Boolean,
    samples: List<TrackSample>,
    prefs: UserPrefs,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(14.dp))
            .background(HudSurface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: title + close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = HudCyan,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = HudCyan)
                }
            }

            // Flight details: airline + route
            FlightDetailsSection(details = details, loading = detailsLoading)

            Spacer(Modifier.height(8.dp))

            // Speed / altitude charts
            when {
                samples.size >= 2 -> Charts(samples, prefs)
                loading -> CenterBox { LoadingRow() }
                error != null -> CenterBox {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                else -> CenterBox {
                    Text("NO FLIGHT HISTORY", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FlightDetailsSection(details: FlightDetails?, loading: Boolean) {
    when {
        loading && details == null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = HudCyan)
                Text("FETCHING ROUTE\u2026", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        details != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Airline + aircraft model row
                val airline = details.airlineName
                val model   = details.aircraftType
                if (airline != null || model != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (airline != null) {
                            Text(
                                airline.uppercase(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = HudAmber,
                            )
                        }
                        if (airline != null && model != null) {
                            Text("·", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (model != null) {
                            Text(
                                model,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Route row: ORIGIN → DEST (show if any IATA or ICAO codes are known)
                val hasRoute = (details.originIata ?: details.originIcao) != null ||
                               (details.destIata   ?: details.destIcao)   != null
                if (hasRoute) {
                    RouteRow(details)
                }
            }
        }
        else -> {
            // No details and not loading — show nothing; don't clutter with "no route"
        }
    }
}

@Composable
private fun RouteRow(details: FlightDetails) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.FlightTakeoff,
            contentDescription = "Origin",
            tint = HudCyan,
            modifier = Modifier.size(14.dp),
        )
        Column {
            Text(
                buildAirportLabel(details.originIata, details.originIcao),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            if (details.originCity != null) {
                Text(details.originCity, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "\u2192",
            fontSize = 14.sp,
            color = HudCyan,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Icon(
            Icons.Filled.FlightLand,
            contentDescription = "Destination",
            tint = HudAmber,
            modifier = Modifier.size(14.dp),
        )
        Column {
            Text(
                buildAirportLabel(details.destIata, details.destIcao),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            if (details.destCity != null) {
                Text(details.destCity, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun buildAirportLabel(iata: String?, icao: String?): String =
    when {
        iata != null -> iata
        icao != null -> icao
        else -> "???"
    }

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = HudCyan)
        Text("LOADING FLIGHT HISTORY\u2026", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Readout("SPD", Formatters.speed(sel.speedKnots, prefs.speedUnit), SpeedColor)
            Readout(
                "ALT",
                sel.altitudeMeters?.let { Formatters.altitude(it, prefs.altitudeUnit) } ?: "--",
                AltColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Formatters.clock(Date(sel.timeMs), prefs.timeFormat),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricChart(xFracs = xFracs, values = samples.map { it.speedKnots }, color = SpeedColor, scrub = scrub, label = { Formatters.speed(it, prefs.speedUnit) })
                MetricChart(xFracs = xFracs, values = samples.map { it.altitudeMeters }, color = AltColor, scrub = scrub, label = { Formatters.altitude(it, prefs.altitudeUnit) })
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Color.White)
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
            textSize = 22f
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val gridColor = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width
        val h = size.height
        val padL = 8f
        val padR = 58f
        val padV = 8f
        val plotW = w - padL - padR
        val plotH = h - padV * 2

        fun px(frac: Float) = padL + frac * plotW
        fun py(value: Double) = padV + (1f - ((value - minV) / range).toFloat()) * plotH

        listOf(0f to maxV, 1f to minV).forEach { (t, v) ->
            val y = padV + t * plotH
            drawLine(gridColor, Offset(padL, y), Offset(padL + plotW, y), 1f)
            drawContext.canvas.nativeCanvas.drawText(label(v), padL + plotW + 4f, y + 7f, labelPaint)
        }

        val path = Path()
        present.forEachIndexed { i, (origIdx, value) ->
            val x = px(xFracs[origIdx])
            val y = py(value!!)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2f))

        if (scrub != null) {
            val x = px(scrub)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(x, padV), Offset(x, padV + plotH), 1.5f)
            val nearest = present.minByOrNull { abs(px(xFracs[it.index]) - x) }
            nearest?.let {
                drawCircle(color, radius = 4f, center = Offset(px(xFracs[it.index]), py(it.value!!)))
            }
        }
    }
}
