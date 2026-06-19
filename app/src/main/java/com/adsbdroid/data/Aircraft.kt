package com.adsbdroid.data

/** Domain model used by the rest of the app (cleaned up from the raw DTO). */
data class Aircraft(
    val hex: String,
    val callsign: String,
    val registration: String?,
    val type: String?,
    val lat: Double,
    val lon: Double,
    /** Geometric (GPS) altitude in meters above mean sea level. */
    val altitudeMeters: Double,
    /** Ground speed in knots. */
    val groundSpeedKnots: Double?,
    /** Track / heading in degrees (0 = north). */
    val headingDeg: Double?,
    /** ADS-B emitter category (e.g. "A1".."A7"), a proxy for aircraft size. */
    val category: String?,
    val onGround: Boolean,
)

private const val FEET_TO_METERS = 0.3048

fun AircraftDto.toDomainOrNull(): Aircraft? {
    val h = hex ?: return null
    val la = lat ?: return null
    val lo = lon ?: return null
    val altFeet = altGeom ?: altBaroFeet ?: 0.0
    return Aircraft(
        hex = h,
        callsign = flight?.trim()?.ifEmpty { null } ?: h.uppercase(),
        registration = r?.trim(),
        type = t?.trim(),
        lat = la,
        lon = lo,
        altitudeMeters = altFeet * FEET_TO_METERS,
        groundSpeedKnots = gs,
        headingDeg = trueHeading ?: track,
        category = category?.trim()?.ifEmpty { null },
        onGround = isOnGround,
    )
}
