package com.sarbikbetal.kestrel.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Whether the device's location services (GPS / network) are currently switched on. */
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Streams fused location updates. Caller must hold ACCESS_FINE_LOCATION
     * before collecting; otherwise a SecurityException is surfaced via the flow.
     */
    @SuppressLint("MissingPermission")
    fun stream(intervalMs: Long = 2_000L): Flow<UserLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    UserLocation(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        altitudeMeters = if (loc.hasAltitude()) loc.altitude else 0.0,
                        accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else Float.NaN,
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
