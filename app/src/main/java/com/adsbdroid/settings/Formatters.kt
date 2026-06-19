package com.adsbdroid.settings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    private const val KNOTS_TO_KMH = 1.852
    private const val KNOTS_TO_MPH = 1.15078
    private const val METERS_TO_FEET = 1.0 / 0.3048

    /** [speedKnots] is the raw ground speed in knots from ADS-B. */
    fun speed(speedKnots: Double?, unit: SpeedUnit): String {
        if (speedKnots == null) return "--"
        val value = when (unit) {
            SpeedUnit.KNOTS -> speedKnots
            SpeedUnit.KMH -> speedKnots * KNOTS_TO_KMH
            SpeedUnit.MPH -> speedKnots * KNOTS_TO_MPH
        }
        val suffix = when (unit) {
            SpeedUnit.KNOTS -> "kt"
            SpeedUnit.KMH -> "km/h"
            SpeedUnit.MPH -> "mph"
        }
        return "${value.roundToInt()} $suffix"
    }

    /** [altitudeMeters] is altitude above mean sea level in meters. */
    fun altitude(altitudeMeters: Double, unit: AltitudeUnit): String = when (unit) {
        AltitudeUnit.FEET -> {
            val feet = (altitudeMeters * METERS_TO_FEET / 100).roundToInt() * 100
            "$feet ft"
        }
        AltitudeUnit.METERS -> {
            val m = (altitudeMeters / 10).roundToInt() * 10
            "$m m"
        }
    }

    fun distanceKm(meters: Double): String {
        val km = (meters / 100).roundToInt() / 10.0
        return "$km km"
    }

    fun clock(date: Date, format: TimeFormat): String {
        val pattern = if (format == TimeFormat.H12) "hh:mm a" else "HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }
}
