package com.sarbikbetal.kestrel.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.sarbikbetal.kestrel.R
import com.sarbikbetal.kestrel.domain.PlaneSize
import com.sarbikbetal.kestrel.domain.PlaneSizeClass
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun userMarker(map: MapView, point: GeoPoint): Marker =
    Marker(map).apply {
        position = point
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_my_location_dot)
        isFlat = true
        setInfoWindow(null)
        title = "You"
    }

/**
 * A translucent cone showing where the device is pointing, anchored at its apex
 * (the user's location). osmdroid rotates flat markers by -rotation (clockwise
 * positive), so the compass [azimuthDeg] is negated to fan toward true bearing.
 */
fun headingMarker(map: MapView, point: GeoPoint, azimuthDeg: Float): Marker =
    Marker(map).apply {
        position = point
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_heading_beam)
        isFlat = true
        rotation = -azimuthDeg
        setInfoWindow(null)
        setOnMarkerClickListener { _, _ -> false }
    }

fun planeMarker(
    map: MapView,
    plane: TrackedPlane,
    prefs: UserPrefs,
    iconScale: Float = 1f,
    selected: Boolean = false,
    onSelect: ((String) -> Unit)? = null,
): Marker = Marker(map).apply {
    position = GeoPoint(plane.lat, plane.lon)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

    val sizeClass = PlaneSize.classify(plane.aircraft.category)
    // Selected planes render at 1.4× size with a contrasting outline around their usual colour.
    val scale = if (selected) iconScale * 1.4f else iconScale
    val dp = markerSizeDp(sizeClass, plane.aircraft.altitudeMeters) * scale
    icon = scaledPlaneIcon(map.context, sizeClass.colorArgb.toInt(), dp, if (selected) SELECTED_STROKE else null)

    isFlat = true
    plane.aircraft.headingDeg?.let { rotation = (-it).toFloat() }

    // Suppress the default osmdroid info bubble; taps route to the details panel.
    setInfoWindow(null)
    if (onSelect != null) {
        val hex = plane.aircraft.hex
        setOnMarkerClickListener { _, _ ->
            onSelect(hex)
            true
        }
    }

    val flag = plane.country?.flag?.let { "$it " } ?: ""
    title = "$flag${plane.aircraft.callsign}"
    snippet = Formatters.altitude(plane.aircraft.altitudeMeters, prefs.altitudeUnit) +
        "  \u00B7  " + Formatters.speed(plane.aircraft.groundSpeedKnots, prefs.speedUnit) +
        "  \u00B7  " + Formatters.distanceKm(plane.sky.slantDistanceMeters)
}

/**
 * Marker size in dp, scaled by aircraft size class and altitude (higher and
 * larger aircraft render bigger), clamped to a sensible on-map range.
 */
private fun markerSizeDp(sizeClass: PlaneSizeClass, altitudeMeters: Double): Float {
    val sizeFactor = when (sizeClass) {
        PlaneSizeClass.LIGHT -> 0.8f
        PlaneSizeClass.SMALL -> 0.95f
        PlaneSizeClass.LARGE -> 1.15f
        PlaneSizeClass.HEAVY -> 1.35f
        PlaneSizeClass.ROTOR -> 0.85f
        PlaneSizeClass.UNKNOWN -> 1.0f
    }
    val altFactor = 0.9f + (altitudeMeters / 12_000.0).coerceIn(0.0, 1.0).toFloat() * 0.4f
    return (22f * sizeFactor * altFactor).coerceIn(15f, 46f)
}

private const val SELECTED_STROKE = 0xFF000000.toInt()
private const val STROKE_DP = 2f

// Rendering vectors to bitmaps is costly, so cache by tint + stroke + rounded size.
private val iconCache = HashMap<String, BitmapDrawable>()

private fun scaledPlaneIcon(context: Context, colorArgb: Int, sizeDp: Float, strokeArgb: Int? = null): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).roundToInt().coerceAtLeast(1)
    val key = "$colorArgb/$strokeArgb@$px"
    iconCache[key]?.let { return it }

    // Outline = the silhouette stamped in the stroke colour around a ring of offsets,
    // then the normal coloured icon on top. Works for any vector shape.
    val pad = if (strokeArgb != null) (STROKE_DP * density).roundToInt() else 0
    val vector = ContextCompat.getDrawable(context, R.drawable.ic_map_plane)!!.mutate()
    val bitmap = Bitmap.createBitmap(px + 2 * pad, px + 2 * pad, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (strokeArgb != null) {
        vector.setTint(strokeArgb)
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            val dx = (pad * cos(a)).roundToInt()
            val dy = (pad * sin(a)).roundToInt()
            vector.setBounds(pad + dx, pad + dy, pad + dx + px, pad + dy + px)
            vector.draw(canvas)
        }
    }
    vector.setTint(colorArgb)
    vector.setBounds(pad, pad, pad + px, pad + px)
    vector.draw(canvas)

    return BitmapDrawable(context.resources, bitmap).also { iconCache[key] = it }
}
