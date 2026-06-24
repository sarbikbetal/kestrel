package com.sarbikbetal.kestrel.data

import android.util.Log
import com.sarbikbetal.kestrel.settings.RouteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches airline, route (origin/destination airports) and full aircraft model for a selected aircraft.
 *
 * Position sources (primary + automatic fallback):
 *  - adsbdb.com  GET /v0/callsign/{callsign} → complete route with airport lat/lon in one call
 *  - hexdb.io    GET /api/v1/route/icao/{callsign} → "KIAD-EGLL" route string
 *              + adsb.lol GET /api/0/airport/{icao} → airport name/city/lat/lon
 *
 * Aircraft info (always hexdb.io, best-effort):
 *  - hexdb.io GET /api/v1/aircraft/{hex} → operator/airline name + full aircraft type
 *
 * Results are cached per "callsign:source" with a 10-minute TTL.
 */
class RouteRepository(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private data class CacheEntry(val details: FlightDetails, val fetchedAtMs: Long)
    private val cache = HashMap<String, CacheEntry>()

    suspend fun fetch(
        callsign: String,
        hex: String,
        @Suppress("UNUSED_PARAMETER") lat: Double,
        @Suppress("UNUSED_PARAMETER") lon: Double,
        routeSource: RouteSource = RouteSource.HEXDB,
    ): Result<FlightDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val cacheKey = "$callsign:${routeSource.name}"
            cache[cacheKey]?.let { entry ->
                if (now - entry.fetchedAtMs < TTL_MS) return@runCatching entry.details
            }

            // Aircraft info (operator name + full model) is always from hexdb — best-effort.
            val acInfo = try { fetchAircraftInfo(hex) } catch (e: Exception) { AircraftInfo(null, null) }

            // Route: try primary source, fall back to secondary on any error.
            val details = try {
                fetchRoute(routeSource, callsign, acInfo)
            } catch (e: Exception) {
                Log.w(TAG, "${routeSource.label} route failed, trying ${routeSource.secondary().label}: ${e.message}")
                try {
                    fetchRoute(routeSource.secondary(), callsign, acInfo)
                } catch (e2: Exception) {
                    Log.w(TAG, "Both route sources failed for $callsign")
                    FlightDetails(airlineName = acInfo.operator, aircraftType = acInfo.fullType)
                }
            }

            cache[cacheKey] = CacheEntry(details, now)
            details
        }
    }

    // -------------------------------------------------------------------------

    /** Fetches route data from [source], returns a complete [FlightDetails] (airports may be null). */
    private fun fetchRoute(source: RouteSource, callsign: String, acInfo: AircraftInfo): FlightDetails =
        when (source) {
            RouteSource.ADSBDB -> fetchAdsbDbRoute(callsign, acInfo)
            RouteSource.HEXDB -> fetchHexDbRoute(callsign, acInfo)
        }

    // --- adsbdb.com -----------------------------------------------------------

    /**
     * One call returns everything: airline name, origin + destination with IATA/ICAO codes,
     * names, city, and lat/lon. No secondary airport lookup needed.
     */
    private fun fetchAdsbDbRoute(callsign: String, acInfo: AircraftInfo): FlightDetails {
        val request = Request.Builder()
            .url("https://api.adsbdb.com/v0/callsign/${callsign.uppercase().trim()}")
            .header("User-Agent", "kestrel")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("adsbdb ${resp.code}")
            val text = resp.body?.string() ?: error("adsbdb empty body")
            return parseAdsbDbRoute(text, acInfo)
        }
    }

    private fun parseAdsbDbRoute(text: String, acInfo: AircraftInfo): FlightDetails {
        val root = json.parseToJsonElement(text).jsonObject
        val route = root["response"]?.jsonObject?.get("flightroute")?.jsonObject
            ?: error("adsbdb: no flightroute in response")
        val airline = route["airline"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
        val origin = route["origin"]?.jsonObject?.let { parseAdsbDbAirport(it) }
        val dest = route["destination"]?.jsonObject?.let { parseAdsbDbAirport(it) }
        return FlightDetails(
            airlineName  = airline ?: acInfo.operator,
            aircraftType = acInfo.fullType,
            originIata   = origin?.iata,
            originIcao   = origin?.icao,
            originName   = origin?.name,
            originCity   = origin?.city,
            originLat    = origin?.lat,
            originLon    = origin?.lon,
            destIata     = dest?.iata,
            destIcao     = dest?.icao,
            destName     = dest?.name,
            destCity     = dest?.city,
            destLat      = dest?.lat,
            destLon      = dest?.lon,
        )
    }

    private fun parseAdsbDbAirport(obj: kotlinx.serialization.json.JsonObject): AirportInfo {
        return AirportInfo(
            iata = obj["iata_code"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() && it != "0" },
            icao = obj["icao_code"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            city = obj["municipality"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            lat  = obj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            lon  = obj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        )
    }

    // --- hexdb.io + adsb.lol --------------------------------------------------

    private fun fetchHexDbRoute(callsign: String, acInfo: AircraftInfo): FlightDetails {
        val routePair = fetchRouteStr(callsign) ?: error("hexdb: no route for $callsign")
        val originApt = try { fetchAirport(routePair.first) } catch (e: Exception) { null }
        val destApt   = try { fetchAirport(routePair.second) } catch (e: Exception) { null }
        return FlightDetails(
            airlineName  = acInfo.operator,
            aircraftType = acInfo.fullType,
            originIata   = originApt?.iata,
            originIcao   = originApt?.icao ?: routePair.first,
            originName   = originApt?.name,
            originCity   = originApt?.city,
            originLat    = originApt?.lat,
            originLon    = originApt?.lon,
            destIata     = destApt?.iata,
            destIcao     = destApt?.icao ?: routePair.second,
            destName     = destApt?.name,
            destCity     = destApt?.city,
            destLat      = destApt?.lat,
            destLon      = destApt?.lon,
        )
    }

    /** Returns (originIcao, destIcao) pair or null if no route found. */
    private fun fetchRouteStr(callsign: String): Pair<String, String>? {
        val request = Request.Builder()
            .url("https://hexdb.io/api/v1/route/icao/${callsign.uppercase().trim()}")
            .header("User-Agent", "kestrel")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            return parseRouteStr(text)
        }
    }

    private fun parseRouteStr(text: String): Pair<String, String>? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val route = obj["route"]?.jsonPrimitive?.content ?: return null
            val parts = route.split("-")
            if (parts.size < 2) return null
            val origin = parts[0].trim().uppercase().takeIf { it.length in 3..4 } ?: return null
            val dest   = parts[1].trim().uppercase().takeIf { it.length in 3..4 } ?: return null
            Pair(origin, dest)
        } catch (e: Exception) { null }
    }

    /** GET adsb.lol airport details by ICAO code. */
    private fun fetchAirport(icao: String): AirportInfo? {
        val request = Request.Builder()
            .url("https://api.adsb.lol/api/0/airport/${icao.uppercase()}")
            .header("User-Agent", "kestrel")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            return parseAirport(text)
        }
    }

    private fun parseAirport(text: String): AirportInfo? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            AirportInfo(
                iata = obj["iata"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() && it != "N/A" },
                icao = obj["icao"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                city = obj["location"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                lat  = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                lon  = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            )
        } catch (e: Exception) { null }
    }

    // --- hexdb aircraft info --------------------------------------------------

    /** GET hexdb.io aircraft record for operator/airline name and full aircraft type. */
    private fun fetchAircraftInfo(hex: String): AircraftInfo {
        val request = Request.Builder()
            .url("https://hexdb.io/api/v1/aircraft/${hex.lowercase()}")
            .header("User-Agent", "kestrel")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return AircraftInfo(null, null)
            val text = resp.body?.string() ?: return AircraftInfo(null, null)
            return parseAircraftInfo(text)
        }
    }

    private fun parseAircraftInfo(text: String): AircraftInfo = try {
        val obj = json.parseToJsonElement(text).jsonObject
        val operator = obj["Operator"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            ?: obj["RegisteredOwners"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val manufacturer = obj["Manufacturer"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val type = obj["Type"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val fullType = when {
            manufacturer != null && type != null -> "$manufacturer $type"
            type != null -> type
            else -> null
        }
        AircraftInfo(operator, fullType)
    } catch (e: Exception) { AircraftInfo(null, null) }

    // -------------------------------------------------------------------------

    private data class AirportInfo(
        val iata: String?,
        val icao: String?,
        val name: String?,
        val city: String?,
        val lat: Double?,
        val lon: Double?,
    )

    private data class AircraftInfo(val operator: String?, val fullType: String?)

    companion object {
        private const val TAG = "RouteRepository"
        private const val TTL_MS = 10 * 60 * 1_000L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
