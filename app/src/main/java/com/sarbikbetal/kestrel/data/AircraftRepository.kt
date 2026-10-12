package com.sarbikbetal.kestrel.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sarbikbetal.kestrel.settings.PositionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Result of one polling cycle. [nextDelayMs] is the planned wait until the next call. */
sealed interface AircraftResult {
    val nextDelayMs: Long
    data class Success(val aircraft: List<Aircraft>, override val nextDelayMs: Long) : AircraftResult
    data class Error(val message: String, override val nextDelayMs: Long) : AircraftResult
}

class AircraftRepository(
    private val api: AdsbApi = defaultApi(),
) {
    /**
     * Emits aircraft near the supplied location on a fixed [intervalMs] cadence.
     * [locationProvider] and [sourceProvider] are polled each tick so callers can
     * feed the latest location and preferred source without restarting the flow.
     * On non-429 failure the secondary source is tried automatically.
     */
    fun stream(
        intervalMs: Long = 8_000L,
        maxBackoffMs: Long = 60_000L,
        radiusNm: Int = 150,
        locationProvider: () -> Pair<Double, Double>?,
        sourceProvider: () -> PositionSource = { PositionSource.ADSB_LOL },
    ): Flow<AircraftResult> = flow {
        var backoffMs = intervalMs
        while (true) {
            val loc = locationProvider()
            var waitMs = intervalMs
            if (loc != null) {
                val (lat, lon) = loc
                val source = sourceProvider()
                try {
                    val planes = fetchWithFallback(source, lat, lon, radiusNm)
                    backoffMs = intervalMs
                    emit(AircraftResult.Success(planes, waitMs))
                } catch (e: HttpException) {
                    if (e.code() == 429) {
                        val retryAfterMs =
                            e.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1000)
                        backoffMs = (retryAfterMs ?: (backoffMs * 2)).coerceIn(intervalMs, maxBackoffMs)
                        waitMs = backoffMs
                        Log.w(TAG, "Rate limited (429); backing off ${backoffMs}ms")
                        emit(AircraftResult.Error("Rate limited - slowing updates", waitMs))
                    } else {
                        Log.w(TAG, "HTTP ${e.code()}: ${e.message}")
                        emit(AircraftResult.Error("Server error ${e.code()}", waitMs))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fetch failed: ${e.message}")
                    emit(AircraftResult.Error(e.message ?: "Network error", waitMs))
                }
            }
            delay(waitMs)
        }
    }

    /**
     * Tries [primary], falls back to the secondary source on any non-429 failure.
     * Throws if both fail (or if the error is a 429).
     */
    private suspend fun fetchWithFallback(
        primary: PositionSource,
        lat: Double,
        lon: Double,
        radiusNm: Int,
    ): List<Aircraft> {
        return try {
            fetchFrom(primary.urlFor(lat, lon, radiusNm))
        } catch (e: HttpException) {
            if (e.code() == 429) throw e
            Log.w(TAG, "${primary.label} failed (${e.code()}), trying ${primary.secondary().label}")
            fetchFrom(primary.secondary().urlFor(lat, lon, radiusNm))
        } catch (e: Exception) {
            Log.w(TAG, "${primary.label} failed, trying ${primary.secondary().label}: ${e.message}")
            fetchFrom(primary.secondary().urlFor(lat, lon, radiusNm))
        }
    }

    private suspend fun fetchFrom(url: String): List<Aircraft> =
        api.aircraftNear(url).ac.mapNotNull { it.toDomainOrNull() }.filter { !it.onGround }

    companion object {
        private const val TAG = "AircraftRepository"
        // Placeholder base URL - overridden per-call via @Url in AdsbApi.
        private const val RETROFIT_BASE = "https://api.adsb.lol/"

        fun defaultApi(): AdsbApi {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(RETROFIT_BASE)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(AdsbApi::class.java)
        }
    }
}
