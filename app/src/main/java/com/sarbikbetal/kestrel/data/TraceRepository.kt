package com.sarbikbetal.kestrel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches an aircraft's full flight trace from adsb.lol's globe host. The trace
 * is the readsb JSON format used by tar1090, where each row is:
 *   [Δtime_s, lat, lon, alt_ft|"ground", gs_kt, track, flags, baro_rate, details, ...]
 * relative to a base "timestamp".
 */
class TraceRepository(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetch(hex: String): Result<List<TrackSample>> = withContext(Dispatchers.IO) {
        runCatching {
            val h = hex.lowercase()
            val dir = h.takeLast(2)
            val url = "https://globe.adsb.lol/data/traces/$dir/trace_full_$h.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "kestrel")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("Empty response")
                parse(body)
            }
        }
    }

    private fun parse(body: String): List<TrackSample> {
        val root = json.parseToJsonElement(body).jsonObject
        val base = root["timestamp"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val trace = root["trace"]?.jsonArray ?: return emptyList()
        return trace.mapNotNull { row ->
            val a = row as? JsonArray ?: return@mapNotNull null
            val dt = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            val lon = a.getOrNull(2)?.jsonPrimitive?.doubleOrNull
            val altEl = a.getOrNull(3)
            val altFeet = when {
                altEl is JsonPrimitive && altEl.isString -> 0.0 // "ground"
                else -> altEl?.jsonPrimitive?.doubleOrNull
            }
            val gs = a.getOrNull(4)?.jsonPrimitive?.doubleOrNull
            TrackSample(
                timeMs = ((base + dt) * 1000).toLong(),
                speedKnots = gs,
                altitudeMeters = altFeet?.let { it * FEET_TO_METERS },
                lat = lat,
                lon = lon,
            )
        }
    }

    companion object {
        private const val FEET_TO_METERS = 0.3048

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
