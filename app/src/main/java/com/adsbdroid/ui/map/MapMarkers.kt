package com.adsbdroid.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.adsbdroid.R
import com.adsbdroid.domain.PlaneSize
import com.adsbdroid.domain.PlaneSizeClass
import com.adsbdroid.settings.Formatters
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

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

fun planeMarker(map: MapView, plane: TrackedPlane, prefs: UserPrefs, iconScale: Float = 1f): Marker =
    Marker(map).apply {
        position = GeoPoint(plane.lat, plane.lon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        val sizeClass = PlaneSize.classify(plane.aircraft.category)
        val dp = markerSizeDp(sizeClass, plane.aircraft.altitudeMeters) * iconScale
        icon = scaledPlaneIcon(map.context, sizeClass.colorArgb.toInt(), dp)

        // Flat markers rotate with the map; osmdroid rotates the canvas by
        // -rotation (clockwise positive), so negate the heading to point the nose
        // along the true compass bearing.
        isFlat = true
        plane.aircraft.headingDeg?.let { rotation = (-it).toFloat() }

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

// Rendering vectors to bitmaps is costly, so cache by tint + rounded size.
private val iconCache = HashMap<String, BitmapDrawable>()

private fun scaledPlaneIcon(context: Context, colorArgb: Int, sizeDp: Float): BitmapDrawable {
    val px = (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val key = "$colorArgb@$px"
    iconCache[key]?.let { return it }

    val vector = ContextCompat.getDrawable(context, R.drawable.ic_map_plane)!!.mutate()
    vector.setTint(colorArgb)
    vector.setBounds(0, 0, px, px)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    vector.draw(Canvas(bitmap))

    return BitmapDrawable(context.resources, bitmap).also { iconCache[key] = it }
}
