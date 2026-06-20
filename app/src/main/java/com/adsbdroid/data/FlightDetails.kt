package com.adsbdroid.data

/** Enriched flight info fetched on demand when a plane is selected. */
data class FlightDetails(
    val airlineName: String? = null,
    /** Full aircraft model string, e.g. "Boeing 777 236ER". */
    val aircraftType: String? = null,
    val originIata: String? = null,
    val originIcao: String? = null,
    val originName: String? = null,
    val originCity: String? = null,
    val destIata: String? = null,
    val destIcao: String? = null,
    val destName: String? = null,
    val destCity: String? = null,
)
