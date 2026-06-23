package com.sarbikbetal.kestrel.data

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
 * Sources:
 *  - hexdb.io GET /api/v1/aircraft/{hex}        → operator/airline name + full aircraft type
 *  - hexdb.io GET /api/v1/route/icao/{callsign} → "KIAD-EGLL" route string
 *  - adsb.lol GET /api/0/airport/{icao}          → airport name, city, IATA code
 *
 * Results are cached per callsign with a 10-minute TTL. Failures from any individual
 * sub-request are swallowed so the panel shows partial data rather than nothing.
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
    ): Result<FlightDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            cache[callsign]?.let { entry ->
                if (now - entry.fetchedAtMs < TTL_MS) return@runCatching entry.details
            }

            // 1. Aircraft info (operator name + full model) from hexdb
            val acInfo = try { fetchAircraftInfo(hex) } catch (e: Exception) { AircraftInfo(null, null) }

            // 2. Route ICAO pair from hexdb (e.g. "KIAD-EGLL" → Pair("KIAD", "EGLL"))
            val routePair = try { fetchRouteStr(callsign) } catch (e: Exception) { null }

            // 3. Airport details from adsb.lol for each leg
            val originApt = routePair?.first?.let  { try { fetchAirport(it) } catch (e: Exception) { null } }
            val destApt   = routePair?.second?.let { try { fetchAirport(it) } catch (e: Exception) { null } }

            val details = FlightDetails(
                airlineName  = acInfo.operator,
                aircraftType = acInfo.fullType,
                originIata   = originApt?.iata,
                originIcao   = originApt?.icao ?: routePair?.first,
                originName   = originApt?.name,
                originCity   = originApt?.city,
                originLat    = originApt?.lat,
                originLon    = originApt?.lon,
                destIata     = destApt?.iata,
                destIcao     = destApt?.icao ?: routePair?.second,
                destName     = destApt?.name,
                destCity     = destApt?.city,
                destLat      = destApt?.lat,
                destLon      = destApt?.lon,
            )
            cache[callsign] = CacheEntry(details, now)
            details
        }
    }

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
            // Format is "ORIGIN-DEST" e.g. "KIAD-EGLL"
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
                lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            )
        } catch (e: Exception) { null }
    }

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

    companion object {
        private const val TTL_MS = 10 * 60 * 1_000L // 10 minutes

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
