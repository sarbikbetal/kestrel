package com.adsbdroid.ui.map

import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Keeps one [Marker] per aircraft and moves it in place each frame, so planes
 * animate toward new positions instead of being rebuilt (which would snap).
 * Markers are only recreated when the aircraft set or [UserPrefs] change.
 */
class PlaneMarkerLayer(
    private val map: MapView,
    private val iconScale: Float = 1f,
) {
    private val markers = HashMap<String, Marker>()
    private var lastPrefs: UserPrefs? = null

    fun sync(planes: List<TrackedPlane>, prefs: UserPrefs, cap: Int = Int.MAX_VALUE) {
        val prefsChanged = prefs != lastPrefs
        lastPrefs = prefs
        val seen = HashSet<String>()

        planes.take(cap).forEach { p ->
            val hex = p.aircraft.hex
            seen += hex
            val existing = markers[hex]
            val marker = if (existing == null || prefsChanged) {
                existing?.let { map.overlays.remove(it) }
                planeMarker(map, p, prefs, iconScale).also {
                    markers[hex] = it
                    map.overlays.add(it)
                }
            } else {
                existing
            }
            marker.position = GeoPoint(p.lat, p.lon)
            p.aircraft.headingDeg?.let { marker.rotation = (-it).toFloat() }
        }

        val iterator = markers.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in seen) {
                map.overlays.remove(entry.value)
                iterator.remove()
            }
        }
    }

    fun clear() {
        markers.values.forEach { map.overlays.remove(it) }
        markers.clear()
        lastPrefs = null
    }
}
