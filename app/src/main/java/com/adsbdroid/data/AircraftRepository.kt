package com.adsbdroid.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
     * The provider lambda lets callers feed the latest known location each tick.
     */
    fun stream(
        intervalMs: Long = 8_000L,
        maxBackoffMs: Long = 60_000L,
        radiusNm: Int = 150,
        locationProvider: () -> Pair<Double, Double>?,
    ): Flow<AircraftResult> = flow {
        // Grows on 429 (rate limit) and resets to [intervalMs] after a clean fetch.
        var backoffMs = intervalMs
        while (true) {
            val loc = locationProvider()
            var waitMs = intervalMs
            if (loc != null) {
                val (lat, lon) = loc
                try {
                    val resp = api.aircraftNear(lat, lon, radiusNm)
                    val planes = resp.ac.mapNotNull { it.toDomainOrNull() }
                        .filter { !it.onGround }
                    backoffMs = intervalMs
                    emit(AircraftResult.Success(planes, waitMs))
                } catch (e: HttpException) {
                    if (e.code() == 429) {
                        val retryAfterMs =
                            e.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1000)
                        backoffMs = (retryAfterMs ?: (backoffMs * 2)).coerceIn(intervalMs, maxBackoffMs)
                        waitMs = backoffMs
                        Log.w(TAG, "Rate limited (429); backing off ${backoffMs}ms")
                        emit(AircraftResult.Error("Rate limited — slowing updates", waitMs))
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

    companion object {
        private const val TAG = "AircraftRepository"

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
                .baseUrl(AdsbApi.BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(AdsbApi::class.java)
        }
    }
}
