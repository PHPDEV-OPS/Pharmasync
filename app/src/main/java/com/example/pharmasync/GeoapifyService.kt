package com.example.pharmasync

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GeoapifyService {
    @GET("v1/geocode/search")
    fun search(
        @Query("text") text: String,
        @Query("apiKey") apiKey: String = "" // You should provide a default or pass it in
    ): Call<GeoapifyResponse>
}
