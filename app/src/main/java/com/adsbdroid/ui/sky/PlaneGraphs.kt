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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.adsbdroid.domain.SkyMath
import com.adsbdroid.settings.Formatters
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import com.adsbdroid.ui.theme.HudAmber
import com.adsbdroid.ui.theme.HudCyan
import com.adsbdroid.ui.theme.HudTextDim
import com.adsbdroid.ui.theme.PanelBg
import com.adsbdroid.ui.theme.PanelBorder
import com.adsbdroid.ui.theme.PanelRow
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SpeedColor = Color(0xFF4FE3D2)
private val AltColor = Color(0xFFFFC857)

/**
 * Flight-details card shown when a plane is selected. Top to bottom: callsign +
 * airline header, an origin → destination strip with a live progress bar, a 2×2
 * stat grid (aircraft / registration / speed / altitude) and the speed/altitude
 * history charts. Populated entirely from data we actually have; sections with no
 * data are omitted rather than faked.
 */
@Composable
fun PlaneGraphsPanel(
    selected: TrackedPlane?,
    fallbackTitle: String,
    details: FlightDetails?,
    detailsLoading: Boolean,
    samples: List<TrackSample>,
    prefs: UserPrefs,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callsign = selected?.aircraft?.callsign ?: fallbackTitle
    val airline = details?.airlineName

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
            .background(PanelBg),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Header: callsign + airline + close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        callsign,
                        style = MaterialTheme.typography.titleLarge,
                        color = HudAmber,
                    )
                    Text(
                        airline ?: if (detailsLoading) "Loading\u2026" else "Unknown operator",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HudTextDim,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Route strip + progress (only when we know at least the airports)
            val hasRoute = details != null &&
                ((details.originIata ?: details.originIcao) != null ||
                    (details.destIata ?: details.destIcao) != null)
            if (hasRoute && details != null) {
                Spacer(Modifier.height(14.dp))
                RouteStrip(details)
                Spacer(Modifier.height(14.dp))
                FlightProgress(details, selected, prefs)
            } else if (detailsLoading) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = HudCyan)
                    Text("FETCHING ROUTE\u2026", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HudTextDim)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Flight information grid
            SectionHeader("FLIGHT INFORMATION")
            Spacer(Modifier.height(8.dp))
            FlightInfoGrid(selected, details, prefs)

            // History charts
            Spacer(Modifier.height(16.dp))
            SectionHeader("SPEED / ALTITUDE")
            Spacer(Modifier.height(8.dp))
            when {
                samples.size >= 2 -> Charts(samples, prefs)
                loading -> CenterBox { LoadingRow() }
                error != null -> CenterBox {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                else -> CenterBox {
                    Text("NO FLIGHT HISTORY", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = HudTextDim)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Route strip: CPH    ✈    LCA
// ---------------------------------------------------------------------------

@Composable
private fun RouteStrip(details: FlightDetails) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AirportColumn(
            code = airportCode(details.originIata, details.originIcao),
            city = details.originCity,
            tz = utcOffsetLabel(details.originLon),
            align = Alignment.Start,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(PanelRow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Flight,
                contentDescription = null,
                tint = HudAmber,
                modifier = Modifier.size(20.dp).rotate(90f),
            )
        }
        AirportColumn(
            code = airportCode(details.destIata, details.destIcao),
            city = details.destCity,
            tz = utcOffsetLabel(details.destLon),
            align = Alignment.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AirportColumn(
    code: String,
    city: String?,
    tz: String?,
    align: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(
            code,
            fontSize = 34.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (city != null) {
            Text(city, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = HudTextDim)
        }
        if (tz != null) {
            Text(tz, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HudTextDim.copy(alpha = 0.7f))
        }
    }
}

// ---------------------------------------------------------------------------
// Progress bar with flown / remaining distance + ETA
// ---------------------------------------------------------------------------

@Composable
private fun FlightProgress(details: FlightDetails, selected: TrackedPlane?, prefs: UserPrefs) {
    val oLat = details.originLat
    val oLon = details.originLon
    val dLat = details.destLat
    val dLon = details.destLon
    val pLat = selected?.lat
    val pLon = selected?.lon

    // Without endpoint coordinates we can only draw an indeterminate track.
    if (oLat == null || oLon == null || dLat == null || dLon == null) {
        ProgressTrack(fraction = 0.5f, indeterminate = true)
        return
    }

    val flownM = if (pLat != null && pLon != null) SkyMath.haversineMeters(oLat, oLon, pLat, pLon) else 0.0
    val remainingM = if (pLat != null && pLon != null) SkyMath.haversineMeters(pLat, pLon, dLat, dLon)
        else SkyMath.haversineMeters(oLat, oLon, dLat, dLon)
    val total = (flownM + remainingM).coerceAtLeast(1.0)
    val fraction = (flownM / total).toFloat().coerceIn(0f, 1f)

    ProgressTrack(fraction = fraction, indeterminate = false)

    Spacer(Modifier.height(6.dp))

    // Remaining time / ETA from current ground speed.
    val speedKnots = selected?.aircraft?.groundSpeedKnots
    val etaText = if (speedKnots != null && speedKnots > 20.0) {
        val hours = (remainingM / 1000.0) / (speedKnots * 1.852)
        val minutes = (hours * 60).roundToLong()
        val arrival = Date(System.currentTimeMillis() + minutes * 60_000L)
        "${formatDuration(minutes)}  \u00B7  ETA ${Formatters.clock(arrival, prefs.timeFormat)}"
    } else null

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${formatKm(flownM)} flown",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = HudTextDim,
        )
        Spacer(Modifier.weight(1f))
        Text(
            buildString {
                append(formatKm(remainingM))
                if (etaText != null) append("  \u00B7  ").append(etaText)
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = HudTextDim,
        )
    }
}

@Composable
private fun ProgressTrack(fraction: Float, indeterminate: Boolean) {
    Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
        val yc = size.height / 2f
        val x0 = 6f
        val x1 = size.width - 6f
        val xp = x0 + (x1 - x0) * fraction
        val track = Color(0xFF2A3845)

        // Remaining (dashed) under everything
        drawLine(
            color = track,
            start = Offset(x0, yc),
            end = Offset(x1, yc),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        if (!indeterminate) {
            // Flown (solid amber)
            drawLine(HudAmber, Offset(x0, yc), Offset(xp, yc), strokeWidth = 3f)
            // Origin dot
            drawCircle(Color.White, radius = 5f, center = Offset(x0, yc))
            // Destination ring
            drawCircle(track, radius = 6f, center = Offset(x1, yc))
            drawCircle(HudTextDim, radius = 6f, center = Offset(x1, yc), style = Stroke(2f))
            // Plane marker pointing along travel (right)
            val s = 9f
            val path = Path().apply {
                moveTo(xp + s, yc)
                lineTo(xp - s, yc - s * 0.7f)
                lineTo(xp - s * 0.4f, yc)
                lineTo(xp - s, yc + s * 0.7f)
                close()
            }
            drawPath(path, HudAmber)
        }
    }
}

// ---------------------------------------------------------------------------
// Flight information 2x2 grid
// ---------------------------------------------------------------------------

@Composable
private fun FlightInfoGrid(selected: TrackedPlane?, details: FlightDetails?, prefs: UserPrefs) {
    val model = details?.aircraftType ?: selected?.aircraft?.type
    val country = selected?.country
    val registration = country?.let { "${it.flag} ${it.name}" } ?: selected?.aircraft?.registration
    val speed = Formatters.speed(selected?.aircraft?.groundSpeedKnots, prefs.speedUnit)
    val altitude = selected?.aircraft?.altitudeMeters?.let { Formatters.altitude(it, prefs.altitudeUnit) } ?: "--"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("AIRCRAFT", model ?: "--", Modifier.weight(1f))
            StatCell("REGISTERED", registration ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("SPEED", speed, Modifier.weight(1f), valueColor = SpeedColor)
            StatCell("ALTITUDE", altitude, Modifier.weight(1f), valueColor = AltColor)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelRow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = HudTextDim)
        Text(
            value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PanelRow)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(HudAmber))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

private fun airportCode(iata: String?, icao: String?): String = iata ?: icao ?: "???"

/** "1 235 km" with space-grouped thousands. */
private fun formatKm(meters: Double): String {
    val km = (meters / 1000.0).roundToInt()
    val grouped = km.toString().reversed().chunked(3).joinToString(" ").reversed()
    return "$grouped km"
}

private fun formatDuration(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Rough local UTC offset estimated from longitude (15° ≈ 1h). Approximate only. */
private fun utcOffsetLabel(lon: Double?): String? {
    if (lon == null) return null
    val offset = (lon / 15.0).roundToInt()
    return when {
        offset == 0 -> "UTC"
        offset > 0 -> "UTC+$offset"
        else -> "UTC$offset"
    }
}

// ---------------------------------------------------------------------------
// Charts (unchanged behaviour, restyled labels)
// ---------------------------------------------------------------------------

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = HudCyan)
        Text("LOADING FLIGHT HISTORY\u2026", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = HudTextDim)
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
                color = HudTextDim,
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
