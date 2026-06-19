package com.adsbdroid.data

/** One observed point in an aircraft's flight trace. */
data class TrackSample(
    val timeMs: Long,
    val speedKnots: Double?,
    val altitudeMeters: Double?,
)
