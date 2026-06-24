package com.sarbikbetal.kestrel.ui.sky

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Material3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarbikbetal.kestrel.data.FlightDetails
import com.sarbikbetal.kestrel.data.TrackSample
import com.sarbikbetal.kestrel.domain.SkyMath
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaneDetailBottomSheet(
    selected: TrackedPlane?,
    fallbackTitle: String,
    details: FlightDetails?,
    detailsLoading: Boolean,
    samples: List<TrackSample>,
    prefs: UserPrefs,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { },
    ) {
        PlaneDetailContent(
            selected = selected,
            fallbackTitle = fallbackTitle,
            details = details,
            detailsLoading = detailsLoading,
            samples = samples,
            prefs = prefs,
            loading = loading,
            error = error,
            onClose = onDismiss,
            modifier = Modifier.padding(top = 20.dp, bottom = 32.dp),
        )
    }
}

@Composable
fun PlaneDetailContent(
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callsign,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = airline ?: if (detailsLoading) "Loading route…" else "Unknown operator",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val hasRoute = details != null &&
            ((details.originIata ?: details.originIcao) != null ||
                (details.destIata ?: details.destIcao) != null)
        if (hasRoute && details != null) {
            Spacer(Modifier.height(20.dp))
            RouteStrip(details)
            Spacer(Modifier.height(16.dp))
            FlightProgress(details, selected, prefs)
        } else if (detailsLoading) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Fetching route…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Flight information")
        Spacer(Modifier.height(12.dp))
        FlightInfoGrid(selected, details, prefs)

        Spacer(Modifier.height(24.dp))
        SectionTitle("Speed & altitude")
        Spacer(Modifier.height(12.dp))
        when {
            samples.size >= 2 -> Charts(samples, prefs)
            loading -> CenterBox { LoadingRow() }
            error != null -> CenterBox {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> CenterBox {
                Text(
                    "No flight history available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

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
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Flight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp).rotate(90f),
                )
            }
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
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (city != null) {
            Text(city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tz != null) {
            Text(
                tz,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(Material3ExpressiveApi::class)
@Composable
private fun FlightProgress(details: FlightDetails, selected: TrackedPlane?, prefs: UserPrefs) {
    val oLat = details.originLat
    val oLon = details.originLon
    val dLat = details.destLat
    val dLon = details.destLon
    val pLat = selected?.lat
    val pLon = selected?.lon

    if (oLat == null || oLon == null || dLat == null || dLon == null) {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        return
    }

    val flownM = if (pLat != null && pLon != null) SkyMath.haversineMeters(oLat, oLon, pLat, pLon) else 0.0
    val remainingM = if (pLat != null && pLon != null) SkyMath.haversineMeters(pLat, pLon, dLat, dLon)
        else SkyMath.haversineMeters(oLat, oLon, dLat, dLon)
    val total = (flownM + remainingM).coerceAtLeast(1.0)
    val fraction = (flownM / total).toFloat().coerceIn(0f, 1f)

    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(8.dp))

    val speedKnots = selected?.aircraft?.groundSpeedKnots
    val etaText = if (speedKnots != null && speedKnots > 20.0) {
        val hours = (remainingM / 1000.0) / (speedKnots * 1.852)
        val minutes = (hours * 60).roundToLong()
        val arrival = Date(System.currentTimeMillis() + minutes * 60_000L)
        "${formatDuration(minutes)} · ETA ${Formatters.clock(arrival, prefs.timeFormat)}"
    } else null

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${formatKm(flownM)} flown",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            buildString {
                append(formatKm(remainingM))
                if (etaText != null) append(" · ").append(etaText)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun FlightInfoGrid(selected: TrackedPlane?, details: FlightDetails?, prefs: UserPrefs) {
    val model = details?.aircraftType ?: selected?.aircraft?.type
    val country = selected?.country
    val registration = country?.let { "${it.flag} ${it.name}" } ?: selected?.aircraft?.registration
    val speed = Formatters.speed(selected?.aircraft?.groundSpeedKnots, prefs.speedUnit)
    val altitude = selected?.aircraft?.altitudeMeters?.let { Formatters.altitude(it, prefs.altitudeUnit) } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell("Aircraft", model ?: "—", Modifier.weight(1f))
            StatCell("Registered", registration ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(
                "Speed",
                speed,
                Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.tertiary,
            )
            StatCell(
                "Altitude",
                altitude,
                Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}

private fun airportCode(iata: String?, icao: String?): String = iata ?: icao ?: "???"

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

private fun utcOffsetLabel(lon: Double?): String? {
    if (lon == null) return null
    val offset = (lon / 15.0).roundToInt()
    return when {
        offset == 0 -> "UTC"
        offset > 0 -> "UTC+$offset"
        else -> "UTC$offset"
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Loading flight history…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    val speedColor = MaterialTheme.colorScheme.tertiary
    val altColor = MaterialTheme.colorScheme.secondary

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Readout("SPD", Formatters.speed(sel.speedKnots, prefs.speedUnit), speedColor)
                Readout(
                    "ALT",
                    sel.altitudeMeters?.let { Formatters.altitude(it, prefs.altitudeUnit) } ?: "—",
                    altColor,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    Formatters.clock(Date(sel.timeMs), prefs.timeFormat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
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
                        color = speedColor,
                        scrub = scrub,
                        label = { Formatters.speed(it, prefs.speedUnit) },
                    )
                    MetricChart(
                        xFracs = xFracs,
                        values = samples.map { it.altitudeMeters },
                        color = altColor,
                        scrub = scrub,
                        label = { Formatters.altitude(it, prefs.altitudeUnit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrubLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    val labelPaint = remember(labelColor) {
        Paint().apply {
            isAntiAlias = true
            this.color = labelColor.toArgb()
            textSize = 22f
        }
    }

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
        drawPath(path, color, style = Stroke(3f))

        if (scrub != null) {
            val x = px(scrub)
            drawLine(scrubLineColor, Offset(x, padV), Offset(x, padV + plotH), 1.5f)
            val nearest = present.minByOrNull { abs(px(xFracs[it.index]) - x) }
            nearest?.let {
                drawCircle(color, radius = 5f, center = Offset(px(xFracs[it.index]), py(it.value!!)))
            }
        }
    }
}
