package com.adsbdroid.settings

enum class SpeedUnit(val label: String) {
    KNOTS("Knots"),
    KMH("km/h"),
    MPH("mph"),
}

enum class AltitudeUnit(val label: String) {
    FEET("Feet"),
    METERS("Meters"),
}

enum class TimeFormat(val label: String) {
    H24("24-hour"),
    H12("12-hour"),
}

enum class MapType(val label: String) {
    STANDARD("Political"),
    TERRAIN("Terrain"),
    HUMANITARIAN("Humanitarian"),
}

data class UserPrefs(
    val speedUnit: SpeedUnit = SpeedUnit.KNOTS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.FEET,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val mapType: MapType = MapType.STANDARD,
    val showAircraft: Boolean = true,
    val showHeadingCone: Boolean = true,
)
