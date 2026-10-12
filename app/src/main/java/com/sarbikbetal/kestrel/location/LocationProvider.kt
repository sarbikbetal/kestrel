package com.sarbikbetal.kestrel.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A user position with altitude in meters above mean sea level. */
data class UserLocation(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
)

class LocationProvider(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Whether the device's location services (GPS / network) are currently switched on. */
    fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(lm)

    /**
     * Streams platform location updates. Caller must hold ACCESS_FINE_LOCATION
     * before collecting; otherwise a SecurityException is surfaced via the flow.
     */
    @SuppressLint("MissingPermission")
    fun stream(intervalMs: Long = 2_000L): Flow<UserLocation> = callbackFlow {
        val request = LocationRequestCompat.Builder(intervalMs)
            .setMinUpdateIntervalMillis(1_000L)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()

        val listener = LocationListenerCompat { loc ->
            trySend(
                UserLocation(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    altitudeMeters = if (loc.hasAltitude()) loc.altitude else 0.0,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else Float.NaN,
                )
            )
        }

        // ponytail: single provider (GPS, else network) instead of GMS fused, so the app
        // stays F-Droid-eligible. Ceiling: slower/rougher first fixes than fused location;
        // upgrade path is a `full` build flavor that keeps play-services-location.
        val provider = lm.allProviders.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: LocationManager.NETWORK_PROVIDER

        LocationManagerCompat.requestLocationUpdates(lm, provider, request, listener, context.mainLooper)
        awaitClose { lm.removeUpdates(listener) }
    }
}
