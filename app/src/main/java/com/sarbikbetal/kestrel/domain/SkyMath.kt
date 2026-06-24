package com.sarbikbetal.kestrel.domain

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a plane sits in the sky relative to the observer. */
data class SkyPosition(
    /** Compass bearing observer -> plane (0 = North, clockwise). */
    val azimuthDeg: Double,
    /** Angle above the horizon (0 = level, 90 = straight up). */
    val elevationDeg: Double,
    /** Horizontal great-circle distance in meters. */
    val groundDistanceMeters: Double,
    /** Straight-line (slant) distance in meters. */
    val slantDistanceMeters: Double,
)

/** Result of mapping a sky position onto the AR overlay. */
data class Projection(
    val onScreen: Boolean,
    val x: Float,
    val y: Float,
    /** Direction from screen center to the target, degrees clockwise from up. */
    val bearingFromCenterDeg: Float,
)

object SkyMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing from point 1 to point 2, in degrees [0,360). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Compute azimuth + elevation of a plane from the observer. */
    fun skyPosition(
        obsLat: Double,
        obsLon: Double,
        obsAltM: Double,
        planeLat: Double,
        planeLon: Double,
        planeAltM: Double,
    ): SkyPosition {
        val ground = haversineMeters(obsLat, obsLon, planeLat, planeLon)
        val azimuth = bearingDeg(obsLat, obsLon, planeLat, planeLon)
        val dAlt = planeAltM - obsAltM
        val elevation = Math.toDegrees(atan2(dAlt, ground))
        val slant = hypot(ground, dAlt)
        return SkyPosition(azimuth, elevation, ground, slant)
    }

    /**
     * Move [distanceM] meters from a start point along a constant compass [bearingDeg]
     * (great-circle). Used to dead-reckon a plane's position between API fetches so it
     * glides smoothly instead of jumping each poll.
     */
    fun destinationPoint(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceM: Double,
    ): Pair<Double, Double> {
        val angular = distanceM / EARTH_RADIUS_M
        val br = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(lat)
        val lam1 = Math.toRadians(lon)
        val phi2 = asin(sin(phi1) * cos(angular) + cos(phi1) * sin(angular) * cos(br))
        val lam2 = lam1 + atan2(
            sin(br) * sin(angular) * cos(phi1),
            cos(angular) - sin(phi1) * sin(phi2),
        )
        return Math.toDegrees(phi2) to Math.toDegrees(lam2)
    }

    /** Normalize an angle difference to [-180, 180]. */
    fun deltaAngle(a: Double, b: Double): Double {
        var d = (a - b + 540.0) % 360.0 - 180.0
        if (d < -180.0) d += 360.0
        return d
    }

    /**
     * Project a sky position onto the screen given where the device points.
     *
     * Uses the same angular scale as SyntheticSky: (heightPx/2) / 50 px per degree.
     * This guarantees that a plane at elevation 0 always lands exactly on the
     * drawn horizon line regardless of device tilt, matching the visual sky perfectly.
     * Roll is applied identically to how the star field rotates, keeping plane icons
     * locked to the sky on all three axes.
     */
    fun project(
        planeAzimuthDeg: Double,
        planeElevationDeg: Double,
        deviceAzimuthDeg: Double,
        deviceElevationDeg: Double,
        deviceRollDeg: Double,
        widthPx: Float,
        heightPx: Float,
    ): Projection {
        val dAz = deltaAngle(planeAzimuthDeg, deviceAzimuthDeg)
        val dEl = planeElevationDeg - deviceElevationDeg

        // Matches SyntheticSky: (h/2) / 50 px per degree.
        val pxPerDeg = heightPx / 2.0 / 50.0

        // Raw pixel offsets from screen centre.
        // Positive dEl → plane is above device pointing direction → negative screen-y (up).
        val rawDx = dAz * pxPerDeg
        val rawDy = -dEl * pxPerDeg

        // Apply roll rotation around screen centre — identical to star-field rotation.
        val r = Math.toRadians(deviceRollDeg)
        val cosR = cos(r); val sinR = sin(r)
        val rotDx = rawDx * cosR - rawDy * sinR
        val rotDy = rawDx * sinR + rawDy * cosR

        val x = (widthPx  / 2f + rotDx).toFloat()
        val y = (heightPx / 2f + rotDy).toFloat()

        val onScreen = abs(rotDx) <= widthPx  / 2f * 1.05 &&
                       abs(rotDy) <= heightPx / 2f * 1.05 &&
                       abs(dAz) < 100.0   // behind-the-observer guard

        val bearing = (Math.toDegrees(atan2(rotDx, -rotDy)) + 360.0) % 360.0
        return Projection(onScreen, x, y, bearing.toFloat())
    }
}
