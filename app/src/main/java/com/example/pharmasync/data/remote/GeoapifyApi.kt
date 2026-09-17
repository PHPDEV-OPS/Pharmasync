package com.example.pharmasync.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** Optional geocoding fallback, used only when GEOAPIFY_API_KEY is set in local.properties. */
interface GeoapifyApi {
    @GET("v1/geocode/search")
    suspend fun search(
        @Query("text") text: String,
        @Query("apiKey") apiKey: String,
        @Query("limit") limit: Int = 1,
    ): GeoapifyResponse

    companion object {
        const val BASE_URL = "https://api.geoapify.com/"
    }
}

data class GeoapifyResponse(val features: List<GeoapifyFeature>? = null)

data class GeoapifyFeature(val geometry: GeoapifyGeometry? = null)

/** GeoJSON order: [longitude, latitude]. */
data class GeoapifyGeometry(val coordinates: List<Double>? = null)
