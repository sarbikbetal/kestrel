package com.sarbikbetal.kestrel.data

import retrofit2.http.GET
import retrofit2.http.Url

interface AdsbApi {
    /**
     * Fetches aircraft near a point. The full URL is supplied by the caller so this
     * single method works with any adsb.lol-compatible endpoint (adsb.lol, airplanes.live…).
     */
    @GET
    suspend fun aircraftNear(@Url url: String): AdsbResponse
}
