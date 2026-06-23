package com.sarbikbetal.kestrel.data

import retrofit2.http.GET
import retrofit2.http.Path

interface AdsbApi {
    /**
     * Returns aircraft within [distNm] nautical miles of the given point.
     * adsb.lol caps the radius at 250 nm.
     */
    @GET("v2/lat/{lat}/lon/{lon}/dist/{dist}")
    suspend fun aircraftNear(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Path("dist") distNm: Int,
    ): AdsbResponse

    companion object {
        const val BASE_URL = "https://api.adsb.lol/"
    }
}
