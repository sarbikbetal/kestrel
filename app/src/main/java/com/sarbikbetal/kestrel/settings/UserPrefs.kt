package com.sarbikbetal.kestrel.settings

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

enum class PositionSource(val label: String) {
    ADSB_LOL("adsb.lol"),
    AIRPLANES_LIVE("airplanes.live");

    fun secondary() = if (this == ADSB_LOL) AIRPLANES_LIVE else ADSB_LOL

    fun urlFor(lat: Double, lon: Double, radiusNm: Int): String = when (this) {
        ADSB_LOL -> "https://api.adsb.lol/v2/lat/$lat/lon/$lon/dist/$radiusNm"
        AIRPLANES_LIVE -> "https://api.airplanes.live/v2/point/$lat/$lon/$radiusNm"
    }
}

enum class RouteSource(val label: String) {
    HEXDB("hexdb.io"),
    ADSBDB("adsbdb.com");

    fun secondary() = if (this == HEXDB) ADSBDB else HEXDB
}

data class UserPrefs(
    val speedUnit: SpeedUnit = SpeedUnit.KNOTS,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.FEET,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val mapType: MapType = MapType.STANDARD,
    val showAircraft: Boolean = true,
    val showHeadingCone: Boolean = true,
    val positionSource: PositionSource = PositionSource.ADSB_LOL,
    val routeSource: RouteSource = RouteSource.HEXDB,
)
