package com.sarbikbetal.kestrel.domain

/**
 * Coarse size buckets derived from the ADS-B emitter category, each with a
 * distinct accent color (ARGB). Used to color plane markers in both the AR
 * overlay and the map view.
 */
enum class PlaneSizeClass(val label: String, val colorArgb: Long) {
    LIGHT("Light", 0xFF6BE675),       // small props / GA
    SMALL("Small", 0xFF4FE3D2),       // regional / business jets
    LARGE("Large", 0xFFFFC857),       // narrow-body airliners
    HEAVY("Heavy", 0xFFFF7043),       // wide-body / heavy
    ROTOR("Rotorcraft", 0xFFB388FF),  // helicopters
    UNKNOWN("Aircraft", 0xFF9FB3C8),  // category not transmitted
}

object PlaneSize {
    /** Maps an ADS-B emitter category (e.g. "A3") to a [PlaneSizeClass]. */
    fun classify(category: String?): PlaneSizeClass = when (category?.uppercase()) {
        "A1" -> PlaneSizeClass.LIGHT
        "A2" -> PlaneSizeClass.SMALL
        "A3" -> PlaneSizeClass.LARGE
        "A4", "A5" -> PlaneSizeClass.HEAVY
        "A7" -> PlaneSizeClass.ROTOR
        else -> PlaneSizeClass.UNKNOWN
    }
}
