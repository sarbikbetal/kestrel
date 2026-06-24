package com.sarbikbetal.kestrel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Response envelope from adsb.lol v2 radius query:
 * https://api.adsb.lol/v2/lat/{lat}/lon/{lon}/dist/{distNm}
 */
@Serializable
data class AdsbResponse(
    val ac: List<AircraftDto> = emptyList(),
    val total: Int = 0,
    val now: Long = 0,
)

/**
 * A single aircraft record. Many fields are optional because not every
 * aircraft transmits every value. `alt_baro` can also be the string "ground".
 */
@Serializable
data class AircraftDto(
    val hex: String? = null,
    val flight: String? = null,
    val r: String? = null,
    val t: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("alt_baro") val altBaro: JsonElement? = null,
    @SerialName("alt_geom") val altGeom: Double? = null,
    val gs: Double? = null,
    val track: Double? = null,
    @SerialName("true_heading") val trueHeading: Double? = null,
    val category: String? = null,
    @SerialName("seen_pos") val seenPos: Double? = null,
    @SerialName("baro_rate") val baroRate: Double? = null,
    val dst: Double? = null,
    val dir: Double? = null,
) {
    /** Barometric altitude in feet, or null if on ground / unknown. */
    val altBaroFeet: Double?
        get() = when (val a = altBaro) {
            is JsonPrimitive -> if (a.isString) null else a.doubleOrNull
            else -> null
        }

    val isOnGround: Boolean
        get() = (altBaro as? JsonPrimitive)?.takeIf { it.isString }?.content == "ground"
}
