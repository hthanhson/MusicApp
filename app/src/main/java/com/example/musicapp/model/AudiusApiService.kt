package com.example.musicapp.model

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AudiusApiService {
    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String
    ): AudiusResponse
}





