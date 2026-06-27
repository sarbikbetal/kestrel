package com.sarbikbetal.kestrel.data

/** One observed point in an aircraft's flight trace. */
data class TrackSample(
    val timeMs: Long,
    val speedKnots: Double?,
    val altitudeMeters: Double?,
    val lat: Double? = null,
    val lon: Double? = null,
)
